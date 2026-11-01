# Runepixels

Sync your Old School RuneScape progress to your profile on [Runepixels](https://runepixels.com), track your boss kill times and look up other players.

Every feature is **disabled by default**. Enable only what you want to share in the plugin settings, under **Sync** and **Tools**. Synced data is shown publicly on your Runepixels profile.

## Sync categories

| Category | What is sent | When |
|---|---|---|
| **Quests** | The status of every quest (not started, in progress, completed) and your quest points | On login, every few minutes and when you open a quest journal |
| **Achievement diaries** | Your progress on every diary task and tier | On login and whenever a diary task is completed |
| **Combat achievements** | Which combat tasks you have completed and the tier thresholds | On login and whenever a combat task is completed |
| **Collection log** | The items in your collection log and their counts, plus collection log drops as you receive them | When you open the collection log and when you receive a drop |
| **Clan** | Your clan name and its members' names and ranks, only if you are the clan owner or a deputy owner | On login, when the clan changes |
| **Boss kill times** | The boss, kill count, fight duration and personal best of each of your kills | After each kill that shows a fight duration |

### Collection log

The game only sends your collection log to the client when you open it, so it cannot be synced automatically:

1. Open the **Collection log** in game and click the **Search** button. This sends your whole log at once.
2. Collection log drops from bosses, raids, clues and minigames are recorded automatically, with the date, as you receive them.
3. Click **Search** again whenever you want to update your item counts.

Items you obtained before using the plugin are synced without a date, because the game does not store when they were obtained.

### Boss kill times

A kill is recorded when the game shows both the kill count and the fight duration in the chat. Your best time for each boss is shown on your Runepixels profile.

The **Timers** tab of the Runepixels panel lists your kills per boss (up to 100 per boss, stored on this PC), with the drops received right after each kill.

- **Remove** deletes the selected kill and **Clear** deletes all your kills for that boss. Your best time on your profile is kept.
- **Share all** shows your times in the global list. Only players who share are listed, with their best time for the chosen period (today, yesterday, week, month or year).

## Player lookup

Enable **Player lookup** under **Tools** to add **Runepixels lookup** to the right-click menu of players. It opens the **Lookup** tab of the Runepixels panel with that player's public Runepixels profile: recent activity, drops per period and collection log with best times. **My lookup** opens your own profile.

The option is hidden in the Wilderness and on PvP worlds. Only the looked-up display name is sent.

## Privacy

- Your account is identified by your account hash and your display name.
- Nothing is sent until you enable a feature, and a confirmation is shown when you enable a sync category.
- When you disable a category nothing more is sent. Data already on Runepixels is kept.
- Nothing is sent about other players except the display name you look up.
- Seasonal, Deadman, beta, tournament, speedrun, fresh start and Last Man Standing worlds are never synced.
