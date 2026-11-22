import { Component, computed, input } from "@angular/core";

/**
 * A Riot ID's name and #tag: "Kestrel#EUW" is "Kestrel" and "#EUW". Everything from the first #
 * is the tag; a name without one has no tag.
 */
export const splitRiotId = (riotId: string): { name: string; tag: string } => {
  const at = riotId.indexOf("#");

  return at === -1
    ? { name: riotId, tag: "" }
    : { name: riotId.slice(0, at), tag: riotId.slice(at) };
};

/** A Riot ID as the site shows it everywhere: the name, then anything from a # in a lighter grey. */
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
