import { provideHttpClient, withFetch } from "@angular/common/http";
import type { ApplicationConfig } from "@angular/core";
import { isDevMode, provideBrowserGlobalErrorListeners } from "@angular/core";
import { provideRouter, withComponentInputBinding } from "@angular/router";
import { provideTransloco } from "@jsverse/transloco";

import { routes } from "./app.routes";
import { LANGUAGES } from "./core/language";
import { TranslocoHttpLoader } from "./core/transloco-loader";

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(withFetch()),
    provideRouter(routes, withComponentInputBinding()),
    provideTransloco({
      config: {
        availableLangs: [...LANGUAGES],
        defaultLang: "en",
        fallbackLang: "en",
        prodMode: !isDevMode(),
        reRenderOnLangChange: true,
      },
      loader: TranslocoHttpLoader,
    }),
  ],
};
