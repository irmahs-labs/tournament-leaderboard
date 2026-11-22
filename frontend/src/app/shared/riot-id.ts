import { Component, computed, input } from "@angular/core";

/** A Riot ID's name and #tag: "Kestrel#EUW" is "Kestrel" and "#EUW". An old one may have no tag. */
export const splitRiotId = (riotId: string): { name: string; tag: string } => {
  const at = riotId.lastIndexOf("#");

  return at > 0
    ? { name: riotId.slice(0, at), tag: riotId.slice(at) }
    : { name: riotId, tag: "" };
};

/**
 * Whether it's a Riot ID as Riot makes them: a name of 3 to 16 characters, # and a tag of 3 to 5
 * letters or numbers, such as Kestrel#EUW. The API checks the same.
 */
export const isRiotId = (value: string): boolean =>
  /^[^#]{3,16}#[\p{L}\p{N}]{3,5}$/u.test(value.trim());

/** A Riot ID as the site shows it everywhere: the name, then its #tag in a lighter grey. */
@Component({
  selector: "app-riot-id",
  styles: `
    .tag {
      color: var(--muted);
      font-weight: 500;
      text-transform: none;
    }
  `,
  template: `{{ parts().name }}<span class="tag">{{ parts().tag }}</span>`,
})
export class RiotId {
  readonly value = input.required<string | null>();

  protected readonly parts = computed(() => splitRiotId(this.value() ?? ""));
}
