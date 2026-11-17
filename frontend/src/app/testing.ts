import { provideHttpClient } from "@angular/common/http";
import { provideHttpClientTesting } from "@angular/common/http/testing";
import type { EnvironmentProviders, Provider } from "@angular/core";
import { importProvidersFrom } from "@angular/core";
import { provideRouter, withComponentInputBinding } from "@angular/router";
import { TranslocoTestingModule } from "@jsverse/transloco";

import en from "../../public/i18n/en.json";
import fr from "../../public/i18n/fr.json";
import { routes } from "./app.routes";
import type { Organization } from "./core/organization";

export const ALSACE: Organization = {
  accentColor: "#2F5BFF",
  defaultLocale: "fr",
  description: null,
  name: "Alsace Arena",
  playersPerMatch: 10,
  slug: "alsace-arena",
  tournamentName: "Spike Tournament",
};

/** The app's routes, a fake back end, and both languages loaded from the real files. */
export const testProviders = (): (Provider | EnvironmentProviders)[] => [
  provideHttpClient(),
  provideHttpClientTesting(),
  provideRouter(routes, withComponentInputBinding()),
  importProvidersFrom(
    TranslocoTestingModule.forRoot({
      langs: { en, fr },
      preloadLangs: true,
      translocoConfig: {
        availableLangs: ["en", "fr"],
        defaultLang: "en",
        reRenderOnLangChange: true,
      },
    })
  ),
];
