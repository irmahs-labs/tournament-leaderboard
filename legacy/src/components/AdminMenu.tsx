'use client';

import { useRef, useState, useTransition } from 'react';
import { useRouter } from 'next/navigation';
import { IconEye, IconEyeOff } from '@tabler/icons-react';
import { createClient } from '@/lib/supabase/client';
import { getDictionary, type Locale } from '@/i18n/dictionary';

const badgeClass =
  'text-[11px] font-semibold text-[var(--accent)] border border-[var(--accent-33)] hover:border-[var(--accent-77)] px-2.5 py-1 rounded tracking-[.06em] uppercase transition-colors';

export default function AdminMenu({ isAdmin, locale }: { isAdmin: boolean; locale: Locale }) {
  const t = getDictionary(locale).admin;
  const dialogRef = useRef<HTMLDialogElement>(null);
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [pending, startTransition] = useTransition();
  const [showPassword, setShowPassword] = useState(false);

  if (isAdmin) {
    return (
      <a href="/admin" className={badgeClass}>
        {t.badge}
      </a>
    );
  }

  function open() {
    setError(null);
    setShowPassword(false);
    dialogRef.current?.showModal();
  }

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);

    const form = new FormData(e.currentTarget);
    const email = String(form.get('email') ?? '').trim();
    const password = String(form.get('password') ?? '');

    const supabase = createClient();
    const { data, error: signInError } = await supabase.auth.signInWithPassword({ email, password });
    if (signInError || !data.user) {
      setError(t.wrongCredentials);
      return;
    }

    const { data: profile } = await supabase
      .from('profiles')
      .select('is_admin')
      .eq('id', data.user.id)
      .single();

    if (!profile?.is_admin) {
      await supabase.auth.signOut();
      setError(t.notAdmin);
      return;
    }

    dialogRef.current?.close();
    startTransition(() => {
      router.refresh();
      router.push('/admin');
    });
  }

  return (
    <>
      <button type="button" onClick={open} className={badgeClass}>
        {t.badge}
      </button>

      <dialog
        ref={dialogRef}
        onClick={(e) => {
          if (e.target === dialogRef.current) dialogRef.current?.close();
        }}
        className="fixed inset-0 m-0 h-full max-h-none w-full max-w-none items-center justify-center bg-transparent p-5 backdrop:bg-black/60 open:flex"
      >
        <div className="max-h-full w-[380px] max-w-full overflow-y-auto rounded-xl border border-[var(--border)] bg-[var(--bg)] p-7">
          <div className="mb-5 flex items-start justify-between">
            <div>
              <div className="mb-1 text-[11px] font-semibold uppercase tracking-[.14em] text-[var(--accent)]">{t.overline}</div>
              <h2 className="font-display text-[20px] font-bold leading-none text-[var(--text-strong)]">{t.signInTitle}</h2>
            </div>
            <button
              type="button"
              onClick={() => dialogRef.current?.close()}
              className="text-[18px] leading-none text-[var(--text-muted)] hover:text-[var(--text-strong)]"
              aria-label={t.close}
            >
              ×
            </button>
          </div>

          {error && (
            <div className="mb-4 rounded-md border border-[var(--accent-33)] bg-[var(--accent-0f)] px-3 py-2 text-[12px] text-[var(--accent)]">
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit} className="flex flex-col gap-3.5">
            <label className="flex flex-col gap-1.5">
              <span className="text-[11px] font-semibold uppercase tracking-[.06em] text-[var(--text-muted)]">{t.email}</span>
              <input
                type="email"
                name="email"
                autoComplete="email"
                required
                className="rounded-md border border-[var(--border)] bg-[var(--surface)] px-3 py-2.5 text-[13px] text-[var(--text)] focus:border-[var(--accent-55)] focus:outline-none"
              />
            </label>

            <label className="flex flex-col gap-1.5">
              <span className="text-[11px] font-semibold uppercase tracking-[.06em] text-[var(--text-muted)]">{t.password}</span>
              <div className="relative">
                <input
                  type={showPassword ? 'text' : 'password'}
                  name="password"
                  autoComplete="current-password"
                  required
                  className="w-full rounded-md border border-[var(--border)] bg-[var(--surface)] px-3 py-2.5 pr-10 text-[13px] text-[var(--text)] focus:border-[var(--accent-55)] focus:outline-none"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((v) => !v)}
                  className="absolute inset-y-1 right-1 flex w-7 items-center justify-center rounded text-[var(--text-muted)] hover:text-[var(--text-strong)]"
                  aria-label={showPassword ? t.hidePassword : t.showPassword}
                >
                  {showPassword ? <IconEyeOff size={16} /> : <IconEye size={16} />}
                </button>
              </div>
            </label>

            <button
              type="submit"
              disabled={pending}
              className="mt-1 rounded-md bg-[var(--accent)] px-4 py-2.5 text-[13px] font-semibold uppercase tracking-[.04em] text-white transition-colors hover:bg-[var(--accent-hover)] disabled:opacity-60"
            >
              {pending ? t.signingIn : t.signIn}
            </button>
          </form>
        </div>
      </dialog>
    </>
  );
}
