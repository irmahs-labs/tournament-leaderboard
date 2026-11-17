#!/usr/bin/env python3
"""Compares the new API with the live site, after an import.

For every season, and for every match of the export, it reads the live site's table (points,
the eight stats, the five bonus counts and the wins of each player) and the new API's answer,
and lists every difference. The formula only uses whole numbers, so any difference is a bug.

    deploy/import/parity-check.py --new http://localhost:8080

Reads the matches to compare from deploy/import/data/matches.csv, so export first. Uses only
Python's standard library. Exits with 1 when anything differs.
"""

import argparse
import csv
import html
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

LIVE = "https://alsace-arena-spike-tournament.vercel.app"
# The live table's columns after "#" and the player, and what each is in the new API.
STATS = ["PERFORMANCE_SCORE", "KILLS", "DEATHS", "ASSISTS", "ECON_RATING", "FIRST_BLOODS", "PLANTS", "DEFUSES"]
BONUSES = ["FB", "LD", "MA", "MP", "MD"]
FIELDS = STATS + BONUSES + ["wins", "points"]


def fetch(url):
    request = urllib.request.Request(url, headers={"User-Agent": "parity-check"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read().decode("utf-8")


def cell_text(cell):
    return html.unescape(re.sub(r"<[^>]+>", "", cell)).strip()


def live_table(base, season, match=None):
    """The live site's table: {player: {field: number}}. A dash on the page is 0."""
    query = {"currentSeason": season}
    if match:
        query.update(tab=match["match_number"], matchId=match["id"], matchSeason=season)
    page = fetch(base + "/?" + urllib.parse.urlencode(query))
    table = re.search(r"<table.*?</table>", page, re.S)
    players = {}
    for row in re.findall(r"<tr.*?</tr>", table.group(0), re.S) if table else []:
        cells = [cell_text(cell) for cell in re.findall(r"<td[^>]*>(.*?)</td>", row, re.S)]
        if len(cells) != 2 + len(FIELDS):
            continue
        numbers = [0 if value in ("—", "") else int(value) for value in cells[2:]]
        players[cells[1]] = dict(zip(FIELDS, numbers))
    return players


def new_table(standings):
    """The new API's standings in the same shape as the live table."""
    players = {}
    for standing in standings:
        row = {stat: standing["totals"].get(stat, 0) for stat in STATS}
        row.update({code: standing["bonuses"].get(code, 0) for code in BONUSES})
        row.update(wins=standing["wins"], points=standing["points"])
        players[standing["riotId"]] = row
    return players


def differences(where, live, new):
    found = []
    for player in sorted(set(live) | set(new)):
        if player not in new:
            found.append(f"{where}: {player} is on the live site but not in the new API")
        elif player not in live:
            found.append(f"{where}: {player} is in the new API but not on the live site")
        else:
            for field in FIELDS:
                if live[player][field] != new[player][field]:
                    found.append(
                        f"{where}: {player} {field} is {live[player][field]} on the live site, "
                        f"{new[player][field]} in the new API"
                    )
    return found


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--new", required=True, help="the new app's address, such as http://localhost:8080")
    parser.add_argument("--live", default=LIVE, help="the live site's address")
    parser.add_argument("--slug", default="alsace-arena")
    parser.add_argument("--data", default=Path(__file__).parent / "data", type=Path)
    args = parser.parse_args()

    with open(args.data / "matches.csv", newline="", encoding="utf-8") as file:
        matches = list(csv.DictReader(file))
    seasons = sorted({int(match["match_season"]) for match in matches})
    api = f"{args.new.rstrip('/')}/api/orgs/{args.slug}/seasons"

    found, compared = [], 0
    for season in seasons:
        live = live_table(args.live, season)
        new = new_table(json.loads(fetch(f"{api}/{season}/leaderboard"))["standings"])
        found += differences(f"Season {season}", live, new)
        compared += len(live)
        for match in sorted((m for m in matches if int(m["match_season"]) == season), key=lambda m: int(m["id"])):
            where = f"Season {season}, match {match['match_number']}"
            live = live_table(args.live, season, match)
            new = new_table(json.loads(fetch(f"{api}/{season}/matches/{match['match_number']}"))["combined"])
            found += differences(where, live, new)
            compared += len(live)
            time.sleep(0.2)  # go easy on the live site
        print(f"Season {season}: checked", file=sys.stderr)

    print(f"{len(seasons)} seasons, {len(matches)} matches, {compared} player rows compared.")
    if found:
        print(f"{len(found)} differences:")
        print("\n".join(found))
        return 1
    print("No differences.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except urllib.error.URLError as error:
        sys.exit(f"Couldn't reach a site: {error}")
