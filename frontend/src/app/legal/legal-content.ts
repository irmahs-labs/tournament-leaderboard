import type { Language } from "../shared/language";

/**
 * The legal pages, in English and French: the legal notice (LCEN article 1-1), the privacy policy
 * (GDPR articles 13 and 14), the cookie policy (CNIL guidance on article 82 of the loi
 * Informatique et Libertés) and the terms of use. Prose, so it lives here rather than in the
 * translation files. Update `UPDATED` and the text together when what the site does changes.
 */
export type LegalPageId = "legal" | "privacy" | "cookies" | "terms";

export const LEGAL_PAGES: readonly LegalPageId[] = [
  "legal",
  "privacy",
  "cookies",
  "terms",
];

export interface LegalLink {
  readonly text: string;
  readonly href: string;
}

export interface LegalTable {
  readonly caption: string;
  readonly head: readonly string[];
  readonly rows: readonly (readonly string[])[];
}

export interface LegalSection {
  readonly heading: string;
  readonly paragraphs?: readonly string[];
  readonly items?: readonly string[];
  readonly table?: LegalTable;
  readonly links?: readonly LegalLink[];
}

export interface LegalDocument {
  readonly title: string;
  readonly intro: string;
  readonly sections: readonly LegalSection[];
}

export const CONTACT = "tournament-leaderboard@irmahs.dev";

const MAIL = `mailto:${CONTACT}`;
const CNIL_COMPLAINT = "https://www.cnil.fr/fr/adresser-une-plainte";
const SOURCE = "https://github.com/irmahs-labs/tournament-leaderboard";

const HOST_EN =
  "Hetzner Online GmbH, Industriestr. 25, 91710 Gunzenhausen, Germany. Phone: +49 9831 505-0.";

const HOST_FR =
  "Hetzner Online GmbH, Industriestr. 25, 91710 Gunzenhausen, Allemagne. Téléphone : +49 9831 505-0.";

const EN: Record<LegalPageId, LegalDocument> = {
  cookies: {
    intro:
      "This site uses no advertising, analytics or social media trackers. It only stores what it needs to work and to remember your choices, so it doesn't ask for your consent and shows no cookie banner.",
    sections: [
      {
        heading: "What is stored in your browser",
        paragraphs: [
          "Under the French rules on trackers (article 82 of the loi Informatique et Libertés), trackers that are strictly necessary for a service you ask for, such as signing in, security, or remembering the language and look you picked, need no consent. All of the ones below are of that kind.",
        ],
        table: {
          caption: "Cookies and browser storage used by the site",
          head: ["Name", "Kind", "What it's for", "How long"],
          rows: [
            [
              "XSRF-TOKEN",
              "Cookie, this site",
              "Security: proves that a change comes from this site (protection against cross-site request forgery).",
              "Until you close the browser",
            ],
            [
              "JSESSIONID",
              "Cookie, this site, only once you sign in",
              "Keeps you signed in. It can't be read by scripts.",
              "Until you sign out, or 30 days after you signed in",
            ],
            [
              "lang",
              "Local storage, only if you pick a language",
              "Remembers the language you picked.",
              "Until you clear your browser's data",
            ],
            [
              "theme",
              "Local storage, only if you pick a theme",
              "Remembers whether you picked the dark or the light theme.",
              "Until you clear your browser's data",
            ],
            [
              "signInReturnTo",
              "Session storage, only while signing in",
              "Brings you back to the page you were on after signing in with Google.",
              "Deleted after signing in, or when you close the tab",
            ],
          ],
        },
      },
      {
        heading: "Signing in with Google",
        links: [
          {
            href: "https://policies.google.com/technologies/cookies",
            text: "How Google uses cookies",
          },
        ],
        paragraphs: [
          "When you choose to sign in, you're sent to Google's sign-in page, where Google sets its own cookies under its own policy. This site doesn't receive them.",
        ],
      },
      {
        heading: "Managing them",
        paragraphs: [
          "You can delete these cookies and this storage at any time in your browser's settings. The site keeps working: you'll just be signed out, and the language and theme go back to their defaults.",
          "If this site ever adds a tracker that needs consent, it will ask for it first, and this page will be updated.",
        ],
      },
    ],
    title: "Cookie policy",
  },
  legal: {
    intro:
      "Who publishes and hosts this site, as required by article 1-1 of the French law for confidence in the digital economy (LCEN, law no. 2004-575 of 21 June 2004).",
    sections: [
      {
        heading: "Publisher",
        links: [{ href: MAIL, text: CONTACT }],
        paragraphs: [
          "Tournament Leaderboard is a free, non-commercial site published by Irma HS, a private individual acting on a non-professional basis. As article 1-1 II of the LCEN allows, the publisher's identification details have been given to the host, who keeps them confidential.",
          `Director of publication: Irma HS. Contact: ${CONTACT}.`,
        ],
      },
      {
        heading: "Host",
        links: [{ href: "https://www.hetzner.com", text: "www.hetzner.com" }],
        paragraphs: [HOST_EN],
      },
      {
        heading: "Content",
        paragraphs: [
          "Each organisation's results, players and seasons are entered by that organisation's admins, who are responsible for them. To report content that is wrong or unlawful, write to the contact address above.",
          "Valorant and Riot Games are trademarks of Riot Games, Inc. This site isn't endorsed by or affiliated with Riot Games.",
        ],
      },
      {
        heading: "Accessibility",
        paragraphs: [
          "The site aims to meet the WCAG 2.1 level AA guidelines: it can be used with a keyboard and a screen reader, its colours are checked for contrast in both themes, and it follows your browser's text size. If something stops you from using it, write to the contact address and it will be fixed.",
        ],
      },
      {
        heading: "Source code",
        links: [{ href: SOURCE, text: "irmahs-labs/tournament-leaderboard" }],
        paragraphs: ["The site's code is open source under the MIT licence."],
      },
    ],
    title: "Legal notice",
  },
  privacy: {
    intro:
      "What personal data this site keeps, why, for how long, and your rights over it, under the General Data Protection Regulation (GDPR) and the French loi Informatique et Libertés. The site only keeps what it needs to show tournament results and to let organisers sign in.",
    sections: [
      {
        heading: "Who is responsible",
        links: [{ href: MAIL, text: CONTACT }],
        paragraphs: [
          `The data controller is Irma HS, who publishes the site (see the legal notice). For anything about your data, write to ${CONTACT}.`,
          "Each organisation's admins choose which players and results to publish for their tournament. Requests about a player's data can be sent to the contact address, which passes them on to the organisation when needed.",
        ],
      },
      {
        heading: "What is kept, and why",
        paragraphs: [
          "Giving this data is never required to view the site. Signing in is only for organisers; without it you can still see every result. The site uses no analytics, no advertising and no automated decision-making, and it sells nothing. It has no contact form: writing to the contact address uses your own email.",
        ],
        table: {
          caption: "Personal data kept by the site",
          head: ["Whose", "What", "Why, and on what basis", "How long"],
          rows: [
            [
              "Players of a tournament",
              "Riot ID and earlier Riot IDs or nicknames, current rank, results of each game (kills, deaths, assists and other stats), points. Entered by the organisation's admins; the Riot ID is the public name players use in the game.",
              "Publishing the tournament's results and standings, which is what players take part in. Legitimate interest of the organisers (GDPR article 6(1)(f)).",
              "As long as the organisation keeps its tournament history, or until you object (see your rights).",
            ],
            [
              "Players of a tournament",
              "Discord username, if the organisation adds it. Only the organisation's admins see it, and its members while they pick which profile is theirs; it is never public.",
              "Letting the admins recognise and contact players. Legitimate interest of the organisers (article 6(1)(f)).",
              "Until the admins remove it, or until you object.",
            ],
            [
              "Players of a tournament",
              "The seasons you signed up for, when, whether you withdrew, and whether an admin signed you up or withdrew you. Only you and the organisation's admins see it.",
              "Organising the season's matches. Necessary for the service you ask for by signing up (article 6(1)(b)); when an admin signs you up, legitimate interest of the organisers (article 6(1)(f)).",
              "As long as the organisation keeps its tournament history, or until you object.",
            ],
            [
              "Players of a tournament",
              "Your answer for each match (in, waiting list or can't make it), when, and whether you or an admin gave it. The organisation's admins see it; you see your own.",
              "Organising each match's players. Necessary for the service you ask for by answering (article 6(1)(b)); when an admin answers for you, legitimate interest of the organisers (article 6(1)(f)).",
              "As long as the organisation keeps its tournament history, or until you object.",
            ],
            [
              "People who sign in (organisers)",
              "Google account id, email address and name, sent by Google when you sign in; which organisations you belong to and your role there. The site doesn't keep your Google picture or your password: it never sees it.",
              "Signing you in and letting you manage your organisation, which you ask for by signing in. Necessary for the service you use (article 6(1)(b)).",
              "As long as your account exists. Ask for its deletion at any time.",
            ],
            [
              "People who sign in",
              "Your sign-in session: what Google sent when you signed in (account id, email address and name), kept in the site's database so an update of the site doesn't sign you out.",
              "Keeping you signed in, which you ask for by signing in. Necessary for the service you use (article 6(1)(b)).",
              "Deleted when you sign out, or 30 days after your last visit.",
            ],
            [
              "People who sign in",
              "Your notices: news about your matches, your claims and, for admins, what other admins changed, with when you read them.",
              "Telling you what changed in your organisations, which you ask for by joining one. Necessary for the service you use (article 6(1)(b)).",
              "Until 90 days after you read them, or until your account is deleted.",
            ],
            [
              "People invited to an organisation",
              "The email address the invitation is for.",
              "Making sure only that person can use it. Necessary for the service (article 6(1)(b)).",
              "The invitation stops working when it expires (48 hours at most). It is deleted on request.",
            ],
            [
              "Every visitor",
              "Technical data needed to deliver the pages, such as your IP address, handled by the network provider Cloudflare. The server itself keeps no log of visits. The header's online count is the number of pages open right now, counted from their open connections and kept only in memory: it identifies no one and nothing is stored.",
              "Delivering the site and protecting it from attacks. Legitimate interest (article 6(1)(f)).",
              "Under Cloudflare's retention rules.",
            ],
          ],
        },
      },
      {
        heading: "Who receives it",
        items: [
          "Hetzner Online GmbH (Germany): hosts the server and its database.",
          "Cloudflare, Inc. (United States): delivers the pages and protects the site. Data it handles can be transferred to the United States; Cloudflare has certified to the EU-U.S. Data Privacy Framework, which the European Commission considers adequate.",
          "Google LLC (United States): signs organisers in with their Google account. Google has certified to the EU-U.S. Data Privacy Framework. Google uses your account under its own privacy policy.",
        ],
        links: [
          {
            href: "https://www.cloudflare.com/privacypolicy/",
            text: "Cloudflare's privacy policy",
          },
          {
            href: "https://policies.google.com/privacy",
            text: "Google's privacy policy",
          },
        ],
        paragraphs: [
          "Results and standings are public: anyone can see them on the site and through its public API. Discord usernames and accounts are only visible to the organisation's admins.",
          "The site uses these providers, who only handle the data to provide their service:",
        ],
      },
      {
        heading: "Your rights",
        links: [
          { href: MAIL, text: CONTACT },
          { href: CNIL_COMPLAINT, text: "Complain to the CNIL" },
        ],
        paragraphs: [
          "You can ask to see the data about you, to correct it, to delete it, to limit its use, or to receive it in a reusable format. Where it is kept on the basis of legitimate interest, you can also object to it for reasons related to your situation, for example to have your name removed from a tournament's results.",
          `Write to ${CONTACT}. You'll get an answer within one month. If you think your rights aren't respected, you can complain to the CNIL, the French data protection authority (Service des plaintes, 3 Place de Fontenoy, 75007 Paris).`,
        ],
      },
      {
        heading: "Cookies",
        links: [{ href: "/cookies", text: "Cookie policy" }],
        paragraphs: [
          "The site only uses cookies and browser storage that it needs to work. The cookie policy lists them.",
        ],
      },
    ],
    title: "Privacy policy",
  },
  terms: {
    intro:
      "The rules for using Tournament Leaderboard. Using the site means accepting them; they are short.",
    sections: [
      {
        heading: "The service",
        paragraphs: [
          "Tournament Leaderboard shows the results, standings and statistics of community Valorant tournaments. It is free: there is nothing to buy, no subscription and no payment, so there is nothing to refund.",
          "Anyone can view the results without an account. Organisers sign in with Google to manage their organisation.",
        ],
      },
      {
        heading: "Organisers' accounts",
        items: [
          "Only sign in with your own Google account, and keep it secure.",
          "Admins are responsible for the players, results and seasons they enter: they must be accurate, and players must be told that their results are published.",
          "Remove a player's data when they ask, or pass the request on to the contact address.",
          "An admin's access can be removed if these terms aren't followed.",
        ],
      },
      {
        heading: "Fair use",
        items: [
          "Don't publish content that is unlawful, offensive, or about people who didn't take part.",
          "Don't try to break, overload or get around the site's security.",
          "The public API can be read by people and programs, AI agents included, at a reasonable pace.",
        ],
      },
      {
        heading: "Availability and liability",
        paragraphs: [
          "The site is run on a volunteer basis, as is. It may be unavailable or change at any time. Results are entered by each organisation; the publisher isn't responsible for mistakes in them, but will correct or remove them when told.",
        ],
      },
      {
        heading: "Intellectual property",
        paragraphs: [
          "The site's code is open source under the MIT licence. Organisation names and logos belong to their organisations. Valorant and Riot Games are trademarks of Riot Games, Inc.; this site isn't endorsed by Riot Games.",
        ],
      },
      {
        heading: "Changes and applicable law",
        links: [{ href: MAIL, text: CONTACT }],
        paragraphs: [
          "These terms can change; the date at the top of the page shows the latest version. They are governed by French law. For any question or disagreement, write to the contact address first, so that it can be settled amicably.",
        ],
      },
    ],
    title: "Terms of use",
  },
};

const FR: Record<LegalPageId, LegalDocument> = {
  cookies: {
    intro:
      "Ce site n'utilise aucun traceur publicitaire, de mesure d'audience ou de réseau social. Il ne stocke que ce dont il a besoin pour fonctionner et pour retenir vos choix : il ne demande donc pas votre consentement et n'affiche pas de bandeau cookies.",
    sections: [
      {
        heading: "Ce qui est stocké dans votre navigateur",
        paragraphs: [
          "D'après les règles françaises sur les traceurs (article 82 de la loi Informatique et Libertés), les traceurs strictement nécessaires à un service que vous demandez, comme la connexion, la sécurité ou le choix de la langue et de l'apparence, ne demandent pas de consentement. Tous ceux ci-dessous en font partie.",
        ],
        table: {
          caption: "Cookies et stockage du navigateur utilisés par le site",
          head: ["Nom", "Type", "À quoi il sert", "Durée"],
          rows: [
            [
              "XSRF-TOKEN",
              "Cookie, ce site",
              "Sécurité : prouve qu'une modification vient bien de ce site (protection contre la falsification de requêtes).",
              "Jusqu'à la fermeture du navigateur",
            ],
            [
              "JSESSIONID",
              "Cookie, ce site, seulement une fois connecté",
              "Vous garde connecté. Les scripts ne peuvent pas le lire.",
              "Jusqu'à la déconnexion, ou 30 jours après la connexion",
            ],
            [
              "lang",
              "Stockage local, seulement si vous choisissez une langue",
              "Retient la langue choisie.",
              "Jusqu'à ce que vous effaciez les données du navigateur",
            ],
            [
              "theme",
              "Stockage local, seulement si vous choisissez un thème",
              "Retient le thème choisi, sombre ou clair.",
              "Jusqu'à ce que vous effaciez les données du navigateur",
            ],
            [
              "signInReturnTo",
              "Stockage de session, seulement pendant la connexion",
              "Vous ramène à la page où vous étiez après la connexion avec Google.",
              "Effacé après la connexion, ou à la fermeture de l'onglet",
            ],
          ],
        },
      },
      {
        heading: "La connexion avec Google",
        links: [
          {
            href: "https://policies.google.com/technologies/cookies?hl=fr",
            text: "Comment Google utilise les cookies",
          },
        ],
        paragraphs: [
          "Quand vous choisissez de vous connecter, vous passez par la page de connexion de Google, qui dépose ses propres cookies selon sa propre politique. Ce site ne les reçoit pas.",
        ],
      },
      {
        heading: "Les gérer",
        paragraphs: [
          "Vous pouvez supprimer ces cookies et ce stockage à tout moment dans les réglages de votre navigateur. Le site continue de fonctionner : vous serez simplement déconnecté, et la langue et le thème reviendront à leurs valeurs par défaut.",
          "Si ce site ajoute un jour un traceur soumis au consentement, il le demandera d'abord, et cette page sera mise à jour.",
        ],
      },
    ],
    title: "Politique de cookies",
  },
  legal: {
    intro:
      "Qui édite et héberge ce site, comme le prévoit l'article 1-1 de la loi pour la confiance dans l'économie numérique (LCEN, loi n° 2004-575 du 21 juin 2004).",
    sections: [
      {
        heading: "Éditeur",
        links: [{ href: MAIL, text: CONTACT }],
        paragraphs: [
          "Tournament Leaderboard est un site gratuit et non commercial édité par Irma HS, personne physique agissant à titre non professionnel. Comme le permet l'article 1-1 II de la LCEN, les éléments d'identification de l'éditeur ont été communiqués à l'hébergeur, qui les garde confidentiels.",
          `Responsable de la publication : Irma HS. Contact : ${CONTACT}.`,
        ],
      },
      {
        heading: "Hébergeur",
        links: [{ href: "https://www.hetzner.com", text: "www.hetzner.com" }],
        paragraphs: [HOST_FR],
      },
      {
        heading: "Contenu",
        paragraphs: [
          "Les résultats, joueurs et saisons de chaque organisation sont saisis par ses administrateurs, qui en sont responsables. Pour signaler un contenu erroné ou illicite, écrivez à l'adresse de contact ci-dessus.",
          "Valorant et Riot Games sont des marques de Riot Games, Inc. Ce site n'est ni approuvé par Riot Games ni affilié à Riot Games.",
        ],
      },
      {
        heading: "Accessibilité",
        paragraphs: [
          "Le site vise le niveau AA des règles WCAG 2.1 : il s'utilise au clavier et avec un lecteur d'écran, le contraste de ses couleurs est vérifié dans les deux thèmes, et il suit la taille de texte de votre navigateur. Si quelque chose vous empêche de l'utiliser, écrivez à l'adresse de contact et ce sera corrigé.",
        ],
      },
      {
        heading: "Code source",
        links: [{ href: SOURCE, text: "irmahs-labs/tournament-leaderboard" }],
        paragraphs: ["Le code du site est open source, sous licence MIT."],
      },
    ],
    title: "Mentions légales",
  },
  privacy: {
    intro:
      "Les données personnelles que ce site conserve, pourquoi, combien de temps, et vos droits, selon le Règlement général sur la protection des données (RGPD) et la loi Informatique et Libertés. Le site ne garde que ce qu'il lui faut pour afficher les résultats des tournois et permettre aux organisateurs de se connecter.",
    sections: [
      {
        heading: "Responsable du traitement",
        links: [{ href: MAIL, text: CONTACT }],
        paragraphs: [
          `Le responsable du traitement est Irma HS, qui édite le site (voir les mentions légales). Pour toute question sur vos données, écrivez à ${CONTACT}.`,
          "Les administrateurs de chaque organisation choisissent les joueurs et les résultats publiés pour leur tournoi. Les demandes sur les données d'un joueur peuvent être envoyées à l'adresse de contact, qui les transmet à l'organisation si besoin.",
        ],
      },
      {
        heading: "Ce qui est conservé, et pourquoi",
        paragraphs: [
          "Aucune de ces données n'est exigée pour consulter le site. La connexion ne sert qu'aux organisateurs ; sans elle, tous les résultats restent visibles. Le site n'utilise ni mesure d'audience, ni publicité, ni décision automatisée, et ne vend rien. Il n'a pas de formulaire de contact : écrire à l'adresse de contact passe par votre propre messagerie.",
        ],
        table: {
          caption: "Données personnelles conservées par le site",
          head: [
            "Qui",
            "Quoi",
            "Pourquoi, et sur quelle base",
            "Combien de temps",
          ],
          rows: [
            [
              "Joueurs d'un tournoi",
              "Riot ID et anciens Riot ID ou pseudos, rang actuel, résultats de chaque partie (éliminations, morts, assistances et autres statistiques), points. Saisis par les administrateurs de l'organisation ; le Riot ID est le nom public que les joueurs utilisent dans le jeu.",
              "Publier les résultats et le classement du tournoi, auquel les joueurs participent. Intérêt légitime des organisateurs (article 6(1)(f) du RGPD).",
              "Tant que l'organisation conserve l'historique de son tournoi, ou jusqu'à votre opposition (voir vos droits).",
            ],
            [
              "Joueurs d'un tournoi",
              "Nom d'utilisateur Discord, si l'organisation l'ajoute. Seuls les administrateurs de l'organisation le voient, ainsi que ses membres quand ils choisissent leur profil ; il n'est jamais public.",
              "Permettre aux administrateurs de reconnaître et de contacter les joueurs. Intérêt légitime des organisateurs (article 6(1)(f)).",
              "Jusqu'à ce que les administrateurs le retirent, ou jusqu'à votre opposition.",
            ],
            [
              "Joueurs d'un tournoi",
              "Les saisons auxquelles vous vous êtes inscrit, quand, si vous vous êtes désinscrit, et si un administrateur vous a inscrit ou désinscrit. Seuls vous et les administrateurs de l'organisation le voient.",
              "Organiser les matchs de la saison. Nécessaire au service que vous demandez en vous inscrivant (article 6(1)(b)) ; quand un administrateur vous inscrit, intérêt légitime des organisateurs (article 6(1)(f)).",
              "Tant que l'organisation conserve l'historique de son tournoi, ou jusqu'à votre opposition.",
            ],
            [
              "Joueurs d'un tournoi",
              "Votre réponse pour chaque match (vous jouez, liste d'attente ou indisponible), quand, et si c'est vous ou un administrateur qui l'a donnée. Les administrateurs de l'organisation la voient ; vous voyez la vôtre.",
              "Organiser les joueurs de chaque match. Nécessaire au service que vous demandez en répondant (article 6(1)(b)) ; quand un administrateur répond pour vous, intérêt légitime des organisateurs (article 6(1)(f)).",
              "Tant que l'organisation conserve l'historique de son tournoi, ou jusqu'à votre opposition.",
            ],
            [
              "Personnes qui se connectent (organisateurs)",
              "Identifiant du compte Google, adresse e-mail et nom, transmis par Google à la connexion ; les organisations dont vous faites partie et votre rôle. Le site ne garde ni votre photo Google ni votre mot de passe : il ne le voit jamais.",
              "Vous connecter et vous permettre de gérer votre organisation, ce que vous demandez en vous connectant. Nécessaire au service utilisé (article 6(1)(b)).",
              "Tant que votre compte existe. Vous pouvez demander sa suppression à tout moment.",
            ],
            [
              "Personnes qui se connectent",
              "Votre session de connexion : ce que Google a transmis à la connexion (identifiant du compte, adresse e-mail et nom), gardé dans la base de données du site pour qu'une mise à jour du site ne vous déconnecte pas.",
              "Vous garder connecté, ce que vous demandez en vous connectant. Nécessaire au service utilisé (article 6(1)(b)).",
              "Supprimée à la déconnexion, ou 30 jours après votre dernière visite.",
            ],
            [
              "Personnes qui se connectent",
              "Vos notifications : les nouvelles de vos matchs, de vos demandes et, pour les admins, ce que les autres admins ont changé, avec la date à laquelle vous les avez lues.",
              "Vous dire ce qui a changé dans vos organisations, ce que vous demandez en en rejoignant une. Nécessaire au service que vous utilisez (article 6(1)(b)).",
              "Jusqu'à 90 jours après leur lecture, ou jusqu'à la suppression de votre compte.",
            ],
            [
              "Personnes invitées dans une organisation",
              "L'adresse e-mail à laquelle l'invitation est destinée.",
              "Garantir que seule cette personne peut l'utiliser. Nécessaire au service (article 6(1)(b)).",
              "L'invitation ne fonctionne plus à son expiration (48 heures au plus). Elle est supprimée sur demande.",
            ],
            [
              "Tout visiteur",
              "Données techniques nécessaires pour afficher les pages, comme votre adresse IP, traitées par le fournisseur réseau Cloudflare. Le serveur lui-même ne garde aucun journal des visites. Le nombre de personnes en ligne, dans l'en-tête, compte les pages ouvertes à cet instant d'après leurs connexions ouvertes, en mémoire seulement : il n'identifie personne et rien n'est enregistré.",
              "Délivrer le site et le protéger des attaques. Intérêt légitime (article 6(1)(f)).",
              "Selon les règles de conservation de Cloudflare.",
            ],
          ],
        },
      },
      {
        heading: "Qui y a accès",
        items: [
          "Hetzner Online GmbH (Allemagne) : héberge le serveur et sa base de données.",
          "Cloudflare, Inc. (États-Unis) : délivre les pages et protège le site. Les données qu'il traite peuvent être transférées aux États-Unis ; Cloudflare a adhéré au Data Privacy Framework UE–États-Unis, que la Commission européenne juge adéquat.",
          "Google LLC (États-Unis) : connecte les organisateurs avec leur compte Google. Google a adhéré au Data Privacy Framework UE–États-Unis, et utilise votre compte selon sa propre politique de confidentialité.",
        ],
        links: [
          {
            href: "https://www.cloudflare.com/fr-fr/privacypolicy/",
            text: "Politique de confidentialité de Cloudflare",
          },
          {
            href: "https://policies.google.com/privacy?hl=fr",
            text: "Règles de confidentialité de Google",
          },
        ],
        paragraphs: [
          "Les résultats et les classements sont publics : chacun peut les voir sur le site et par son API publique. Les noms Discord et les comptes ne sont visibles que des administrateurs de l'organisation.",
          "Le site fait appel à ces prestataires, qui ne traitent les données que pour fournir leur service :",
        ],
      },
      {
        heading: "Vos droits",
        links: [
          { href: MAIL, text: CONTACT },
          { href: CNIL_COMPLAINT, text: "Adresser une plainte à la CNIL" },
        ],
        paragraphs: [
          "Vous pouvez demander à accéder aux données qui vous concernent, à les rectifier, à les effacer, à en limiter l'utilisation, ou à les recevoir dans un format réutilisable. Pour celles conservées sur la base de l'intérêt légitime, vous pouvez aussi vous y opposer pour des raisons tenant à votre situation, par exemple pour retirer votre nom des résultats d'un tournoi.",
          `Écrivez à ${CONTACT}. Vous recevrez une réponse dans un délai d'un mois. Si vous estimez que vos droits ne sont pas respectés, vous pouvez adresser une réclamation à la CNIL (Service des plaintes, 3 place de Fontenoy, 75007 Paris).`,
        ],
      },
      {
        heading: "Cookies",
        links: [{ href: "/cookies", text: "Politique de cookies" }],
        paragraphs: [
          "Le site n'utilise que les cookies et le stockage du navigateur dont il a besoin pour fonctionner. La politique de cookies les détaille.",
        ],
      },
    ],
    title: "Politique de confidentialité",
  },
  terms: {
    intro:
      "Les règles d'utilisation de Tournament Leaderboard. Utiliser le site, c'est les accepter ; elles sont courtes.",
    sections: [
      {
        heading: "Le service",
        paragraphs: [
          "Tournament Leaderboard affiche les résultats, classements et statistiques de tournois communautaires de Valorant. Il est gratuit : rien à acheter, pas d'abonnement ni de paiement, donc rien à rembourser.",
          "Tout le monde peut consulter les résultats sans compte. Les organisateurs se connectent avec Google pour gérer leur organisation.",
        ],
      },
      {
        heading: "Les comptes des organisateurs",
        items: [
          "Connectez-vous uniquement avec votre propre compte Google, et protégez-le.",
          "Les administrateurs sont responsables des joueurs, résultats et saisons qu'ils saisissent : ils doivent être exacts, et les joueurs doivent savoir que leurs résultats sont publiés.",
          "Retirez les données d'un joueur quand il le demande, ou transmettez sa demande à l'adresse de contact.",
          "L'accès d'un administrateur peut être retiré si ces conditions ne sont pas respectées.",
        ],
      },
      {
        heading: "Bon usage",
        items: [
          "Ne publiez pas de contenu illicite, offensant, ou concernant des personnes qui n'ont pas participé.",
          "N'essayez pas de casser, de surcharger ou de contourner la sécurité du site.",
          "L'API publique peut être lue par des personnes et des programmes, agents d'IA compris, à un rythme raisonnable.",
        ],
      },
      {
        heading: "Disponibilité et responsabilité",
        paragraphs: [
          "Le site est géré bénévolement, en l'état. Il peut être indisponible ou évoluer à tout moment. Les résultats sont saisis par chaque organisation ; l'éditeur du site n'est pas responsable de leurs erreurs, mais les corrige ou les retire dès qu'il en est informé.",
        ],
      },
      {
        heading: "Propriété intellectuelle",
        paragraphs: [
          "Le code du site est open source, sous licence MIT. Les noms et logos des organisations leur appartiennent. Valorant et Riot Games sont des marques de Riot Games, Inc. ; ce site n'est pas approuvé par Riot Games.",
        ],
      },
      {
        heading: "Modifications et droit applicable",
        links: [{ href: MAIL, text: CONTACT }],
        paragraphs: [
          "Ces conditions peuvent évoluer ; la date en haut de la page indique la dernière version. Elles sont soumises au droit français. Pour toute question ou tout désaccord, écrivez d'abord à l'adresse de contact, pour trouver une solution amiable.",
        ],
      },
    ],
    title: "Conditions d'utilisation",
  },
};

export const LEGAL: Record<Language, Record<LegalPageId, LegalDocument>> = {
  en: EN,
  fr: FR,
};

/** When the legal pages last changed, as YYYY-MM-DD. */
export const UPDATED = "2026-11-22";
