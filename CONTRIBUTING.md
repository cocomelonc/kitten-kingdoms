# Contributing

Thank you for helping make Kitten Kingdoms kinder and more accessible.

## Before opening a pull request

1. Keep the game offline and child-safe by default.
2. Do not add advertising, analytics, accounts, dynamic code, or network SDKs.
3. Keep English and Russian string resources in sync.
4. Use assets with a clearly documented open-source or public-domain license.
5. Run `./scripts/verify_android.sh`.

## Translations

Translations live in `app/src/main/res/values-XX/strings.xml`. Start by copying
the English `values/strings.xml`, translate every user-facing string, and do
not translate resource names. If adding a third in-game language, extend the
language selector and keep `bundle.language.enableSplit = false` so every
locale remains available after an in-app switch.

## Visual changes

Runtime art is drawn by `KittenKingdomsView` and should remain legible at the
1280×720 logical resolution. Avoid relying only on color to communicate state,
and keep interactive targets at least 48 logical pixels across.

## Commit hygiene

- Never commit a keystore, signing password, local SDK path, or Play credential.
- Keep generated build output out of Git.
- Add the original license text for every new third-party asset.
