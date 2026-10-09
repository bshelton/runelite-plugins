# Motherlode Mine Improved - v0.4.6
![](img/motherlode.png)

<br>

## Features
* Highlight available/depleted ore veins with progress timers.
* Highlight ore veins when they shouldn't be mined.
* Notify when you no longer need to mine to make sure sack doesn't become to full.
* Highlight rockfalls.
* Send ores and golden nuggets collected from the sack to the built-in Loot Tracker.
* Upstairs is mapped to sections to only show the veins in your section.

<br>

## Custom widget
* Show how many golden nuggets you have in total and/ord found this session.
* Show how many pay-dirt is stored (sack and hopper combined).
* Show how many deposits are needed before the sack gets full.
* Show how many pay-dirt you need to mine before depositing.
* Change background color based on the action you need to do.

![](img/widget.png)

<br>

## Loot Tracker
Enable **Loot Tracker** and **Motherlode Mine Improved**, then leave
**General → Track ores found** enabled (on by default). Collecting from the sack
records newly received coal, gold ore, mithril ore, adamantite ore, runite ore,
and golden nuggets under **Motherlode Mine** in Loot Tracker. Mining pay-dirt,
bank withdrawals, and gems are not recorded by this integration.

The built-in **Motherlode Mine** plugin stays disabled because it conflicts with
Improved. This integration does not require it and does not change that conflict.
This fork must be built/run separately until it is distributed through the Plugin
Hub; installing the existing Hub release will not include these changes.

## Config
![](img/config.png)