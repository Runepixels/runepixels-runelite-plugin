# Runepixels

Sync your Old School RuneScape progress to your profile on [Runepixels](https://runepixels.com).

Every category is **disabled by default**. Enable only what you want to share in the plugin settings, under **Sync**. Synced data is shown publicly on your Runepixels profile.

## Sync categories

| Category | What is sent | When |
|---|---|---|
| **Quests** | The status of every quest (not started, in progress, completed) and your quest points | On login, every few minutes and when you open a quest journal |
| **Achievement diaries** | Your progress on every diary task and tier | On login and whenever a diary task is completed |
| **Combat achievements** | Which combat tasks you have completed and the tier thresholds | On login and whenever a combat task is completed |
| **Collection log** | The items in your collection log and their counts, plus collection log drops as you receive them | When you open the collection log and when you receive a drop |
| **Clan** | Your clan name and its members' names and ranks | When a clan owner or deputy owner clicks **Sync clan** in the Runepixels panel |

### Collection log

The game only sends your collection log to the client when you open it, so it cannot be synced automatically:

1. Open the **Collection log** in game and click the **Search** button. This sends your whole log at once.
2. Collection log drops from bosses, raids, clues and minigames are recorded automatically, with the date, as you receive them.
3. Click **Search** again whenever you want to update your item counts.

Items you obtained before using the plugin are synced without a date, because the game does not store when they were obtained.

## Privacy

- Your account is identified by your account hash and your display name.
- Nothing is sent until you enable a category, and a confirmation is shown when you do.
- When you disable a category nothing more is sent. Data already on Runepixels is kept.
- Seasonal, Deadman, beta, tournament, speedrun, fresh start and Last Man Standing worlds are never synced.
