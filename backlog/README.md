# Backlog features

Screens taken out of the app but kept for later. Nothing in this folder is compiled.

## Team Map (`team-map/LocationScreen.kt`)

The Map tab: offline-style team map (parks, river, roads, 200 m / 400 m range rings),
Map/List toggle, selected-teammate card with Message and Directions, and the Priority Alert
card. Everything on it is **sample data** (Arjun, Priya, Imran, Kavya and the alert); it needs
real GPS positions sent over the link before it is useful.

To bring it back:

1. Move `team-map/LocationScreen.kt` to
   `app/src/main/java/com/itantra/app/feature/location/ui/LocationScreen.kt`.
2. In `feature/home/ui/HomeScreen.kt`, add a Map tab again: a `TAB_MAP` constant,
   `NavigationItem("Map", Icons.Rounded.Map)` in `NavItems`, and `TAB_MAP -> LocationScreen()`
   in the `when (selectedTab)` block.

## Org Feed (`org-feed/OrganisationFeed.kt`)

The Pair screen's "Org Feed" tab: a list of organisation QR channels (NDRF Unit 07, Relief Camp
Alpha, Field Operations). **Sample data**, not wired to anything. It depends on QR pairing, which
isn't built yet.

To bring it back, paste the two composables into
`feature/communication/ui/PairingScreen.kt` and give them a place in the Pair flow.
