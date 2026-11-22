import type { Language } from "../shared/language";

/**
 * The Help page's questions and answers, in English and French: for players, for admins, and
 * about one's account. Prose, so it lives here rather than in the translation files. Keep it in
 * step with what the site does.
 */
export interface HelpQuestion {
  readonly id: string;
  readonly question: string;
  readonly answer: readonly string[];
}

export interface HelpSection {
  readonly id: string;
  readonly title: string;
  readonly questions: readonly HelpQuestion[];
}

export interface HelpDocument {
  readonly title: string;
  readonly intro: string;
  readonly sections: readonly HelpSection[];
}

const EN: HelpDocument = {
  intro:
    "How Tournament Leaderboard works, for players, for the admins who run an organisation, and for your account. Can't find your answer? Write to us with the Contact link at the bottom of the page.",
  sections: [
    {
      id: "players",
      questions: [
        {
          answer: [
            "Open the join link your organisation shares (it ends in /org/its-name/join), or press Join on its leaderboard.",
            "Sign in with Google, and you're a player straight away. Then claim your profile, or create one if you've never played there.",
          ],
          id: "join",
          question: "How do I join an organisation?",
        },
        {
          answer: [
            "If you've already played, the admins enter your scores under your Riot ID. Claiming that profile links it to your account: pick it, add a note if it helps, and an admin accepts it. Your results stay exactly where they are.",
            'Never played there? Press "Never played here?" to create your profile at once, with no admin needed.',
          ],
          id: "claim",
          question: "What does claiming my profile mean?",
        },
        {
          answer: [
            'Open My matches in the header. It lists the upcoming matches of every season under way. Press "I\'m in" or "Can\'t make it" for each one.',
            "You can change your answer until the roster is locked, usually on match day. After that, ask an admin.",
          ],
          id: "answer",
          question: "How do I say whether I'm playing a match?",
        },
        {
          answer: [
            "Each match has a number of places. Once they're taken, \"I'm in\" puts you on the waiting list, in the order people answered. Nobody jumps the queue.",
            "When a place frees up, the first person waiting moves up on their own and gets a notice, unless the admins decide by hand.",
          ],
          id: "waiting",
          question: "What is the waiting list?",
        },
        {
          answer: [
            "The bell in the header counts your unread notices: a match planned, moved or called off, a reminder to answer, a place that freed up for you, your claim answered.",
            "News about a match waits 10 minutes, so a quick correction by an admin sends one notice, not three. Opening the bell marks them read; read notices are deleted after 90 days.",
          ],
          id: "notices",
          question: "What are notices?",
        },
        {
          answer: [
            "Either the roster is locked, the match is called off, or it was already played. Ask an admin: they can still change your answer.",
          ],
          id: "locked",
          question: "Why can't I change my answer?",
        },
        {
          answer: [
            "Points come from each game's stats, the bonuses won and the wins, as the season's rules say. The leaderboard's \"How points are counted\" shows them.",
            "Your rank, averages and every match you played are on your player page.",
          ],
          id: "points",
          question: "How are points counted?",
        },
      ],
      title: "For players",
    },
    {
      id: "admins",
      questions: [
        {
          answer: [
            "Admin → Members → Invite players: copy the link and share it, on Discord for example. Anyone who opens it joins as a player. It doesn't expire.",
            "Players who don't want an account can still play: add them on Players, and answer for them on each roster.",
          ],
          id: "invite-players",
          question: "How do I invite players?",
        },
        {
          answer: [
            "Admin → Members → Invite an admin: type the email of their Google account and pick how long the invite lasts. Send them the message it gives you. The invite works once, for that account only.",
          ],
          id: "invite-admin",
          question: "How do I add another admin?",
        },
        {
          answer: [
            "Admin → Schedule → Plan: the date, the start time, where it's played, the reply-by date and the number of places. Players are told 10 minutes later.",
            "Call a match off or put it back on from the same row. Players hear about both.",
          ],
          id: "plan",
          question: "How do I plan a match?",
        },
        {
          answer: [
            "Schedule → Roster shows who's in, the waiting list in order, who can't make it, and every player still available. Move anyone from one list to another, players without an account too.",
            "Reorder the waiting list with the arrows, or turn automatic moves off to decide by hand. Lock the roster on match day so answers stop changing.",
            "\"Remind them\" sends a notice to the players with an account who haven't answered. They're also reminded on their own two days before the reply-by date.",
          ],
          id: "roster",
          question: "How do I manage a match's players?",
        },
        {
          answer: [
            "Admin → Scores: pick the match and the game, its players and their stats, and the manual bonuses. Check the automatic bonuses, then save. The leaderboard updates at once.",
            "A player who isn't on the list yet can be added by Riot ID from the same page.",
          ],
          id: "scores",
          question: "How do I enter scores?",
        },
        {
          answer: [
            "Seasons: start the next season and set its dates and shape (matches, games per match).",
            "Scoring: choose which stats are recorded and what each is worth, the points per win, the KDA points and the bonuses. Apply the changes to this season or keep them for the next one. A finished season's rules can't change.",
          ],
          id: "seasons",
          question: "How do seasons and scoring rules work?",
        },
        {
          answer: [
            "Admin → Claims lists the claims waiting, with the player's name, email and note. Accept to link their account to the profile, or decline with a reason they'll see.",
          ],
          id: "claims",
          question: "How do I answer a claim?",
        },
        {
          answer: [
            "Admin → Players: add a player, edit their Riot ID (the old one stays as an alias, so searches still find it), their Discord and their rank. You can also type a Google email: whoever signs in with it first gets that profile.",
            "Once a player is linked to an account, they keep their Riot ID and Discord up to date themselves.",
          ],
          id: "players",
          question: "How do I manage players?",
        },
        {
          answer: [
            "Admin → Branding: the organisation's name, the tournament's name, its language, its colour, its logo, its social links and the usual number of places per match.",
          ],
          id: "branding",
          question: "How do I change how the organisation looks?",
        },
        {
          answer: [
            "Each organisation has one owner, who can't be removed or made a player. The owner hands ownership to another admin from Members, and stays an admin.",
          ],
          id: "owner",
          question: "What does the owner do?",
        },
      ],
      title: "For admins",
    },
    {
      id: "account",
      questions: [
        {
          answer: [
            "With your Google account. You stay signed in for 30 days after your last visit, even if you close the browser. Sign out from your account.",
          ],
          id: "sign-in",
          question: "How do I sign in?",
        },
        {
          answer: [
            "Your account (your initial at the top right). Your Riot ID (Name#TAG) and Discord are the same in every organisation you belong to.",
          ],
          id: "identity",
          question: "How do I change my Riot ID or Discord?",
        },
        {
          answer: [
            "From your account, typing DELETE. If you own an organisation, hand it to another admin first. Your results stay in the tournaments you played, no longer linked to you.",
          ],
          id: "delete",
          question: "How do I delete my account?",
        },
        {
          answer: [
            "The EN / FR button and the light / dark switch in the header. The site remembers your choice in this browser.",
          ],
          id: "language",
          question: "How do I change the language or the theme?",
        },
      ],
      title: "Your account",
    },
  ],
  title: "Help",
};

const FR: HelpDocument = {
  intro:
    "Le fonctionnement de Tournament Leaderboard, pour les joueurs, pour les admins qui gèrent une organisation, et pour votre compte. Vous ne trouvez pas la réponse ? Écrivez-nous avec le lien Contact en bas de page.",
  sections: [
    {
      id: "players",
      questions: [
        {
          answer: [
            "Ouvrez le lien d'invitation que votre organisation partage (il se termine par /org/son-nom/join), ou appuyez sur Rejoindre sur son classement.",
            "Connectez-vous avec Google : vous êtes joueur aussitôt. Revendiquez ensuite votre profil, ou créez-en un si vous n'avez jamais joué là.",
          ],
          id: "join",
          question: "Comment rejoindre une organisation ?",
        },
        {
          answer: [
            "Si vous avez déjà joué, les admins saisissent vos scores sous votre Riot ID. Revendiquer ce profil le lie à votre compte : choisissez-le, ajoutez une note si besoin, et un admin l'accepte. Vos résultats restent exactement où ils sont.",
            "Jamais joué là ? Appuyez sur « Jamais joué ici ? » pour créer votre profil aussitôt, sans admin.",
          ],
          id: "claim",
          question: "Que veut dire revendiquer mon profil ?",
        },
        {
          answer: [
            "Ouvrez Mes matchs dans l'en-tête. Il liste les prochains matchs de chaque saison en cours. Appuyez sur « Je joue » ou « Je ne peux pas » pour chacun.",
            "Vous pouvez changer votre réponse jusqu'au verrouillage de la composition, en général le jour du match. Ensuite, demandez à un admin.",
          ],
          id: "answer",
          question: "Comment dire si je joue un match ?",
        },
        {
          answer: [
            "Chaque match a un nombre de places. Une fois prises, « Je joue » vous met sur la liste d'attente, dans l'ordre des réponses. Personne ne passe devant.",
            "Quand une place se libère, la première personne en attente monte d'elle-même et reçoit une notification, sauf si les admins décident à la main.",
          ],
          id: "waiting",
          question: "Qu'est-ce que la liste d'attente ?",
        },
        {
          answer: [
            "La cloche de l'en-tête compte vos notifications non lues : un match prévu, déplacé ou annulé, un rappel pour répondre, une place libérée pour vous, votre demande traitée.",
            "Une nouvelle sur un match attend 10 minutes : une correction rapide d'un admin n'envoie qu'une notification, pas trois. Ouvrir la cloche les marque lues ; les notifications lues sont supprimées après 90 jours.",
          ],
          id: "notices",
          question: "Que sont les notifications ?",
        },
        {
          answer: [
            "La composition est verrouillée, le match est annulé, ou il a déjà été joué. Demandez à un admin : il peut encore changer votre réponse.",
          ],
          id: "locked",
          question: "Pourquoi je ne peux pas changer ma réponse ?",
        },
        {
          answer: [
            "Les points viennent des statistiques de chaque partie, des bonus gagnés et des victoires, selon les règles de la saison. « Comment les points sont comptés » sur le classement les montre.",
            "Votre rang, vos moyennes et chaque match joué sont sur votre page joueur.",
          ],
          id: "points",
          question: "Comment les points sont-ils comptés ?",
        },
      ],
      title: "Pour les joueurs",
    },
    {
      id: "admins",
      questions: [
        {
          answer: [
            "Admin → Membres → Inviter des joueurs : copiez le lien et partagez-le, sur Discord par exemple. Toute personne qui l'ouvre rejoint comme joueur. Il n'expire pas.",
            "Les joueurs qui ne veulent pas de compte peuvent quand même jouer : ajoutez-les dans Joueurs, et répondez pour eux dans la composition de chaque match.",
          ],
          id: "invite-players",
          question: "Comment inviter des joueurs ?",
        },
        {
          answer: [
            "Admin → Membres → Inviter un admin : tapez l'e-mail de son compte Google et choisissez la durée de l'invitation. Envoyez-lui le message obtenu. L'invitation ne sert qu'une fois, pour ce compte seulement.",
          ],
          id: "invite-admin",
          question: "Comment ajouter un autre admin ?",
        },
        {
          answer: [
            "Admin → Calendrier → Prévoir : la date, l'heure de début, le lieu, la date limite de réponse et le nombre de places. Les joueurs sont prévenus 10 minutes plus tard.",
            "Annulez un match ou remettez-le depuis la même ligne. Les joueurs sont prévenus dans les deux cas.",
          ],
          id: "plan",
          question: "Comment prévoir un match ?",
        },
        {
          answer: [
            "Calendrier → Composition montre qui vient, la liste d'attente dans l'ordre, qui ne peut pas, et chaque joueur encore disponible. Déplacez n'importe qui d'une liste à l'autre, y compris les joueurs sans compte.",
            "Réordonnez la liste d'attente avec les flèches, ou désactivez la montée automatique pour décider à la main. Verrouillez la composition le jour du match pour figer les réponses.",
            "« Leur rappeler » envoie une notification aux joueurs avec un compte qui n'ont pas répondu. Un rappel part aussi de lui-même deux jours avant la date limite de réponse.",
          ],
          id: "roster",
          question: "Comment gérer les joueurs d'un match ?",
        },
        {
          answer: [
            "Admin → Scores : choisissez le match et la partie, ses joueurs et leurs statistiques, et les bonus manuels. Vérifiez les bonus automatiques, puis enregistrez. Le classement se met à jour aussitôt.",
            "Un joueur absent de la liste peut être ajouté par son Riot ID depuis la même page.",
          ],
          id: "scores",
          question: "Comment saisir les scores ?",
        },
        {
          answer: [
            "Saisons : lancez la saison suivante et réglez ses dates et sa forme (matchs, parties par match).",
            "Barème : choisissez les statistiques enregistrées et leur valeur, les points par victoire, les points de KDA et les bonus. Appliquez les changements à cette saison ou gardez-les pour la suivante. Les règles d'une saison terminée ne changent plus.",
          ],
          id: "seasons",
          question: "Comment fonctionnent les saisons et le barème ?",
        },
        {
          answer: [
            "Admin → Demandes liste les demandes en attente, avec le nom, l'e-mail et la note du joueur. Acceptez pour lier son compte au profil, ou refusez avec une raison qu'il verra.",
          ],
          id: "claims",
          question: "Comment répondre à une demande ?",
        },
        {
          answer: [
            "Admin → Joueurs : ajoutez un joueur, modifiez son Riot ID (l'ancien reste comme alias, les recherches le trouvent encore), son Discord et son rang. Vous pouvez aussi taper un e-mail Google : la première personne qui se connecte avec reçoit ce profil.",
            "Une fois lié à un compte, le joueur tient lui-même son Riot ID et son Discord à jour.",
          ],
          id: "players",
          question: "Comment gérer les joueurs ?",
        },
        {
          answer: [
            "Admin → Image : le nom de l'organisation, celui du tournoi, sa langue, sa couleur, son logo, ses réseaux et le nombre habituel de places par match.",
          ],
          id: "branding",
          question: "Comment changer l'apparence de l'organisation ?",
        },
        {
          answer: [
            "Chaque organisation a un propriétaire, qui ne peut être ni retiré ni repassé joueur. Le propriétaire confie l'organisation à un autre admin depuis Membres, et reste admin.",
          ],
          id: "owner",
          question: "À quoi sert le propriétaire ?",
        },
      ],
      title: "Pour les admins",
    },
    {
      id: "account",
      questions: [
        {
          answer: [
            "Avec votre compte Google. Vous restez connecté 30 jours après votre dernière visite, même en fermant le navigateur. Déconnectez-vous depuis votre compte.",
          ],
          id: "sign-in",
          question: "Comment me connecter ?",
        },
        {
          answer: [
            "Dans votre compte (votre initiale en haut à droite). Votre Riot ID (Nom#TAG) et votre Discord sont les mêmes dans toutes vos organisations.",
          ],
          id: "identity",
          question: "Comment changer mon Riot ID ou mon Discord ?",
        },
        {
          answer: [
            "Depuis votre compte, en tapant DELETE. Si vous êtes propriétaire d'une organisation, confiez-la d'abord à un autre admin. Vos résultats restent dans les tournois joués, sans lien avec vous.",
          ],
          id: "delete",
          question: "Comment supprimer mon compte ?",
        },
        {
          answer: [
            "Le bouton EN / FR et l'interrupteur clair / sombre de l'en-tête. Le site retient votre choix dans ce navigateur.",
          ],
          id: "language",
          question: "Comment changer la langue ou le thème ?",
        },
      ],
      title: "Votre compte",
    },
  ],
  title: "Aide",
};

export const HELP: Readonly<Record<Language, HelpDocument>> = {
  en: EN,
  fr: FR,
};
