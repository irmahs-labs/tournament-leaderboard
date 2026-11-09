#!/usr/bin/env bash
# Prepares a fresh Hetzner Cloud server (Ubuntu 24.04) for the app. Run it once, as root:
#
#   scp deploy/bootstrap.sh root@<server>:
#   ssh root@<server> 'bash bootstrap.sh irma'
#
# The argument is the name of your own sudo user. It gets the SSH keys root has now (the ones
# chosen when the server was created), so you keep access once root login is turned off.
#
# What it does:
#   - creates your sudo user and a `deploy` user for CI, both in the docker group
#   - turns off root login and password sign-in over SSH
#   - installs Docker Engine and Compose from Docker's apt repository, with log rotation
#   - turns on automatic security updates
#   - creates /opt/leaderboard (deploy files) and /opt/backups (database dumps)
#
# Running it again is safe: every step checks before it changes anything.
set -euo pipefail

admin=${1:?usage: bash bootstrap.sh <your-user-name>}

if [ "$(id -u)" -ne 0 ]; then
  echo "Run this as root." >&2
  exit 1
fi
if [ ! -s /root/.ssh/authorized_keys ]; then
  echo "root has no SSH keys, so $admin would have none either. Add a key first." >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive

echo "== Packages and automatic security updates"
apt-get update -q
apt-get upgrade -yq
apt-get install -yq ca-certificates curl unattended-upgrades
cat > /etc/apt/apt.conf.d/20auto-upgrades <<'EOF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
EOF

echo "== Docker"
if ! command -v docker > /dev/null; then
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
  # shellcheck disable=SC1091
  codename=$(. /etc/os-release && echo "$VERSION_CODENAME")
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $codename stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update -q
  apt-get install -yq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
# The "local" log driver rotates container logs, so they can't fill the disk.
mkdir -p /etc/docker
if [ ! -f /etc/docker/daemon.json ]; then
  echo '{ "log-driver": "local" }' > /etc/docker/daemon.json
  systemctl restart docker
fi
systemctl enable --now docker

echo "== Users"
add_user() {
  local name=$1
  id "$name" > /dev/null 2>&1 || useradd --create-home --shell /bin/bash "$name"
  usermod -aG docker "$name"
  install -d -m 700 -o "$name" -g "$name" "/home/$name/.ssh"
  touch "/home/$name/.ssh/authorized_keys"
  chown "$name:$name" "/home/$name/.ssh/authorized_keys"
  chmod 600 "/home/$name/.ssh/authorized_keys"
}
add_user "$admin"
usermod -aG sudo "$admin"
# Copy root's keys to the admin user, without duplicating lines on a second run.
sort -u /root/.ssh/authorized_keys "/home/$admin/.ssh/authorized_keys" -o "/home/$admin/.ssh/authorized_keys"
# Sudo without a password: the user has no password, since sign-in is by SSH key only.
echo "$admin ALL=(ALL) NOPASSWD:ALL" > "/etc/sudoers.d/90-$admin"
chmod 440 "/etc/sudoers.d/90-$admin"
visudo -cq

# The CI deploy job's public key goes in /home/deploy/.ssh/authorized_keys later.
add_user deploy

echo "== Folders"
install -d -m 755 -o deploy -g deploy /opt/leaderboard
install -d -m 750 -o deploy -g deploy /opt/backups

echo "== SSH: keys only, no root login"
# Ubuntu reads sshd_config.d files in name order and the first value wins, so 10- comes
# before the 50-cloud-init.conf that cloud images ship.
cat > /etc/ssh/sshd_config.d/10-hardening.conf <<'EOF'
PermitRootLogin no
PasswordAuthentication no
KbdInteractiveAuthentication no
EOF
sshd -t
systemctl reload ssh

cat <<EOF

Done. Before closing this root session, check in a second terminal that this works:

  ssh $admin@<server> 'sudo true && docker version --format "Docker {{.Server.Version}}"'

From now on, root can't sign in over SSH.
EOF
