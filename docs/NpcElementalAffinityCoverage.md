# NPC Elemental Affinity Filter Coverage

Source: `server/universe/memories.json`

## Current Filter Order

The runtime resolver checks `CUSTOM_ROLE_AFFINITIES` first, then `ROLE_AFFINITIES` in config order. If multiple affinity rules match in the same list, the first one wins.

## Summary

| Element | Count |
|---|---:|
| `FIRE` | 17 |
| `ICE` | 13 |
| `LIFE` | 60 |
| `LIGHTNING` | 19 |
| `NEUTRAL` | 29 |
| `VOID` | 67 |
| `WATER` | 36 |

Total roles: `241`
Neutral roles: `29`
Ambiguous roles: `34`

## Quick Findings

- Expanded filters reduce neutral roles from `164` to `29`.
- Broad substring false positives were tightened for `pig`, `rat`, and `eel`, so roles like `Pigeon`, `Snake_Rattle`, and `Golem_Firesteel` resolve by their intended hints.
- Ambiguous roles are expected when subtype names overlap, such as burnt/frost undead. Current code resolves the first matching filter by order.
- Scaraks are mapped as Life/insect targets and have explicit Fire weakness through `ELEMENT_MULTIPLIERS`.
- Humanoid/faction roles without clear elemental wording can remain neutral by design unless a dedicated faction mapping is added later.

## Ambiguous Matches

| Role | Resolved | All Matches |
|---|---|---|
| `Bat_Ice` | `ICE` | `ICE, LIGHTNING` |
| `Cow_Undead` | `VOID` | `VOID, LIFE` |
| `Horse_Skeleton` | `VOID` | `VOID, LIFE` |
| `Horse_Skeleton_Armored` | `VOID` | `VOID, LIFE` |
| `Owl_Snow` | `ICE` | `ICE, LIGHTNING` |
| `Pig_Undead` | `VOID` | `VOID, LIFE` |
| `Shellfish_Lava` | `FIRE` | `FIRE, WATER` |
| `Skeleton_Burnt_Alchemist` | `VOID` | `VOID, FIRE` |
| `Skeleton_Burnt_Archer` | `VOID` | `VOID, FIRE` |
| `Skeleton_Burnt_Gunner` | `VOID` | `VOID, FIRE` |
| `Skeleton_Burnt_Knight` | `VOID` | `VOID, FIRE` |
| `Skeleton_Burnt_Lancer` | `VOID` | `VOID, FIRE` |
| `Skeleton_Burnt_Praetorian` | `VOID` | `VOID, FIRE` |
| `Skeleton_Burnt_Soldier` | `VOID` | `VOID, FIRE` |
| `Skeleton_Burnt_Wizard` | `VOID` | `VOID, FIRE` |
| `Skeleton_Frost_Archer` | `VOID` | `VOID, ICE` |
| `Skeleton_Frost_Archmage` | `VOID` | `VOID, ICE` |
| `Skeleton_Frost_Fighter` | `VOID` | `VOID, ICE` |
| `Skeleton_Frost_Knight` | `VOID` | `VOID, ICE` |
| `Skeleton_Frost_Mage` | `VOID` | `VOID, ICE` |
| `Skeleton_Frost_Ranger` | `VOID` | `VOID, ICE` |
| `Skeleton_Frost_Scout` | `VOID` | `VOID, ICE` |
| `Skeleton_Frost_Soldier` | `VOID` | `VOID, ICE` |
| `Skeleton_Sand_Archer` | `VOID` | `VOID, FIRE` |
| `Skeleton_Sand_Archmage` | `VOID` | `VOID, FIRE` |
| `Skeleton_Sand_Assassin` | `VOID` | `VOID, FIRE` |
| `Skeleton_Sand_Guard` | `VOID` | `VOID, FIRE` |
| `Skeleton_Sand_Mage` | `VOID` | `VOID, FIRE` |
| `Skeleton_Sand_Ranger` | `VOID` | `VOID, FIRE` |
| `Skeleton_Sand_Scout` | `VOID` | `VOID, FIRE` |
| `Skeleton_Sand_Soldier` | `VOID` | `VOID, FIRE` |
| `Zombie_Burnt` | `VOID` | `VOID, FIRE` |
| `Zombie_Frost` | `VOID` | `VOID, ICE` |
| `Zombie_Sand` | `VOID` | `VOID, FIRE` |

## Neutral Roles

`Fen_Stalker`, `Feran_Burrower`, `Feran_Civilian`, `Feran_Cub`, `Feran_Longtooth`, `Feran_Sharptooth`, `Goblin_Duke`, `Goblin_Hermit`, `Goblin_Lobber`, `Goblin_Miner`, `Goblin_Ogre`, `Goblin_Scrapper`, `Goblin_Thief`, `Outlander_Berserker`, `Outlander_Brute`, `Outlander_Hunter`, `Outlander_Marauder`, `Outlander_Peon`, `Outlander_Priest`, `Outlander_Sorcerer`, `Outlander_Stalker`, `Trork_Brawler`, `Trork_Chieftain`, `Trork_Guard`, `Trork_Hunter`, `Trork_Mauler`, `Trork_Sentry`, `Trork_Shaman`, `Trork_Warrior`

## Roles

| Role | Resolved Affinity | Matched Filters |
|---|---|---|
| `Antelope` | `LIFE` | `LIFE` |
| `Archaeopteryx` | `LIGHTNING` | `LIGHTNING` |
| `Armadillo` | `LIFE` | `LIFE` |
| `Bat` | `LIGHTNING` | `LIGHTNING` |
| `Bat_Ice` | `ICE` | `ICE, LIGHTNING` |
| `Bear_Grizzly` | `LIFE` | `LIFE` |
| `Bear_Polar` | `ICE` | `ICE` |
| `Bison` | `LIFE` | `LIFE` |
| `Bison_Calf` | `LIFE` | `LIFE` |
| `Bluebird` | `LIGHTNING` | `LIGHTNING` |
| `Bluegill` | `WATER` | `WATER` |
| `Boar` | `LIFE` | `LIFE` |
| `Boar_Piglet` | `LIFE` | `LIFE` |
| `Bunny` | `LIFE` | `LIFE` |
| `Cactee` | `LIFE` | `LIFE` |
| `Camel` | `FIRE` | `FIRE` |
| `Camel_Calf` | `FIRE` | `FIRE` |
| `Catfish` | `WATER` | `WATER` |
| `Chicken` | `LIFE` | `LIFE` |
| `Chicken_Chick` | `LIFE` | `LIFE` |
| `Chicken_Desert` | `FIRE` | `FIRE` |
| `Chicken_Desert_Chick` | `FIRE` | `FIRE` |
| `Chicken_Undead` | `VOID` | `VOID` |
| `Clownfish` | `WATER` | `WATER` |
| `Cow` | `LIFE` | `LIFE` |
| `Cow_Calf` | `LIFE` | `LIFE` |
| `Cow_Undead` | `VOID` | `VOID, LIFE` |
| `Crab` | `WATER` | `WATER` |
| `Crawler_Void` | `VOID` | `VOID` |
| `Crocodile` | `WATER` | `WATER` |
| `Crow` | `LIGHTNING` | `LIGHTNING` |
| `Deer_Doe` | `LIFE` | `LIFE` |
| `Deer_Stag` | `LIFE` | `LIFE` |
| `Dragon_Frost` | `ICE` | `ICE` |
| `Duck` | `WATER` | `WATER` |
| `Eel_Moray` | `WATER` | `WATER` |
| `Emberwulf` | `FIRE` | `FIRE` |
| `Eye_Void` | `VOID` | `VOID` |
| `Fen_Stalker` | `NEUTRAL` | `-` |
| `Feran_Burrower` | `NEUTRAL` | `-` |
| `Feran_Civilian` | `NEUTRAL` | `-` |
| `Feran_Cub` | `NEUTRAL` | `-` |
| `Feran_Longtooth` | `NEUTRAL` | `-` |
| `Feran_Sharptooth` | `NEUTRAL` | `-` |
| `Feran_Windwalker` | `LIGHTNING` | `LIGHTNING` |
| `Finch_Green` | `LIGHTNING` | `LIGHTNING` |
| `Flamingo` | `WATER` | `WATER` |
| `Fox` | `LIFE` | `LIFE` |
| `Frog_Green` | `WATER` | `WATER` |
| `Frostgill` | `ICE` | `ICE` |
| `Gecko` | `LIFE` | `LIFE` |
| `Ghoul` | `VOID` | `VOID` |
| `Goat` | `LIFE` | `LIFE` |
| `Goat_Kid` | `LIFE` | `LIFE` |
| `Goblin_Duke` | `NEUTRAL` | `-` |
| `Goblin_Hermit` | `NEUTRAL` | `-` |
| `Goblin_Lobber` | `NEUTRAL` | `-` |
| `Goblin_Miner` | `NEUTRAL` | `-` |
| `Goblin_Ogre` | `NEUTRAL` | `-` |
| `Goblin_Scavenger` | `VOID` | `VOID` |
| `Goblin_Scrapper` | `NEUTRAL` | `-` |
| `Goblin_Thief` | `NEUTRAL` | `-` |
| `Golem_Crystal_Earth` | `LIFE` | `LIFE` |
| `Golem_Crystal_Flame` | `FIRE` | `FIRE` |
| `Golem_Crystal_Frost` | `ICE` | `ICE` |
| `Golem_Crystal_Sand` | `FIRE` | `FIRE` |
| `Golem_Crystal_Thunder` | `LIGHTNING` | `LIGHTNING` |
| `Golem_Firesteel` | `FIRE` | `FIRE` |
| `Hawk` | `LIGHTNING` | `LIGHTNING` |
| `Hedera` | `LIFE` | `LIFE` |
| `Horse` | `LIFE` | `LIFE` |
| `Horse_Foal` | `LIFE` | `LIFE` |
| `Horse_Skeleton` | `VOID` | `VOID, LIFE` |
| `Horse_Skeleton_Armored` | `VOID` | `VOID, LIFE` |
| `Hound_Bleached` | `ICE` | `ICE` |
| `Hyena` | `LIFE` | `LIFE` |
| `Jellyfish_Blue` | `WATER` | `WATER` |
| `Jellyfish_Cyan` | `WATER` | `WATER` |
| `Jellyfish_Green` | `WATER` | `WATER` |
| `Jellyfish_Man_Of_War` | `WATER` | `WATER` |
| `Jellyfish_Red` | `WATER` | `WATER` |
| `Jellyfish_Yellow` | `WATER` | `WATER` |
| `Kweebec_Rootling` | `LIFE` | `LIFE` |
| `Kweebec_Sapling` | `LIFE` | `LIFE` |
| `Kweebec_Seedling` | `LIFE` | `LIFE` |
| `Kweebec_Sproutling` | `LIFE` | `LIFE` |
| `Larva_Silk` | `LIFE` | `LIFE` |
| `Larva_Void` | `VOID` | `VOID` |
| `Leopard_Snow` | `ICE` | `ICE` |
| `Lizard_Sand` | `FIRE` | `FIRE` |
| `Lobster` | `WATER` | `WATER` |
| `Meerkat` | `LIFE` | `LIFE` |
| `Minnow` | `WATER` | `WATER` |
| `Molerat` | `VOID` | `VOID` |
| `Moose_Bull` | `LIFE` | `LIFE` |
| `Moose_Cow` | `LIFE` | `LIFE` |
| `Mosshorn` | `LIFE` | `LIFE` |
| `Mouflon` | `LIFE` | `LIFE` |
| `Mouflon_Lamb` | `LIFE` | `LIFE` |
| `Mouse` | `VOID` | `VOID` |
| `Outlander_Berserker` | `NEUTRAL` | `-` |
| `Outlander_Brute` | `NEUTRAL` | `-` |
| `Outlander_Cultist` | `VOID` | `VOID` |
| `Outlander_Hunter` | `NEUTRAL` | `-` |
| `Outlander_Marauder` | `NEUTRAL` | `-` |
| `Outlander_Peon` | `NEUTRAL` | `-` |
| `Outlander_Priest` | `NEUTRAL` | `-` |
| `Outlander_Sorcerer` | `NEUTRAL` | `-` |
| `Outlander_Stalker` | `NEUTRAL` | `-` |
| `Owl_Brown` | `LIGHTNING` | `LIGHTNING` |
| `Owl_Snow` | `ICE` | `ICE, LIGHTNING` |
| `Parrot` | `LIGHTNING` | `LIGHTNING` |
| `Penguin` | `ICE` | `ICE` |
| `Pig` | `LIFE` | `LIFE` |
| `Pig_Piglet` | `LIFE` | `LIFE` |
| `Pig_Undead` | `VOID` | `VOID, LIFE` |
| `Pig_Wild` | `LIFE` | `LIFE` |
| `Pig_Wild_Piglet` | `LIFE` | `LIFE` |
| `Pigeon` | `LIGHTNING` | `LIGHTNING` |
| `Pike` | `WATER` | `WATER` |
| `Piranha` | `WATER` | `WATER` |
| `Piranha_Black` | `WATER` | `WATER` |
| `Pterodactyl` | `LIGHTNING` | `LIGHTNING` |
| `Pufferfish` | `WATER` | `WATER` |
| `Rabbit` | `LIFE` | `LIFE` |
| `Ram` | `LIFE` | `LIFE` |
| `Ram_Lamb` | `LIFE` | `LIFE` |
| `Raptor_Cave` | `VOID` | `VOID` |
| `Rat` | `VOID` | `VOID` |
| `Raven` | `LIGHTNING` | `LIGHTNING` |
| `Rex_Cave` | `VOID` | `VOID` |
| `Salmon` | `WATER` | `WATER` |
| `Scarak_Broodmother` | `LIFE` | `LIFE` |
| `Scarak_Defender` | `LIFE` | `LIFE` |
| `Scarak_Fighter` | `LIFE` | `LIFE` |
| `Scarak_Louse` | `LIFE` | `LIFE` |
| `Scarak_Seeker` | `LIFE` | `LIFE` |
| `Scorpion` | `FIRE` | `FIRE` |
| `Shadow_Knight` | `VOID` | `VOID` |
| `Shark_Hammerhead` | `WATER` | `WATER` |
| `Sheep` | `LIFE` | `LIFE` |
| `Sheep_Lamb` | `LIFE` | `LIFE` |
| `Shellfish_Lava` | `FIRE` | `FIRE, WATER` |
| `Skeleton_Archer` | `VOID` | `VOID` |
| `Skeleton_Archmage` | `VOID` | `VOID` |
| `Skeleton_Burnt_Alchemist` | `VOID` | `VOID, FIRE` |
| `Skeleton_Burnt_Archer` | `VOID` | `VOID, FIRE` |
| `Skeleton_Burnt_Gunner` | `VOID` | `VOID, FIRE` |
| `Skeleton_Burnt_Knight` | `VOID` | `VOID, FIRE` |
| `Skeleton_Burnt_Lancer` | `VOID` | `VOID, FIRE` |
| `Skeleton_Burnt_Praetorian` | `VOID` | `VOID, FIRE` |
| `Skeleton_Burnt_Soldier` | `VOID` | `VOID, FIRE` |
| `Skeleton_Burnt_Wizard` | `VOID` | `VOID, FIRE` |
| `Skeleton_Fighter` | `VOID` | `VOID` |
| `Skeleton_Frost_Archer` | `VOID` | `VOID, ICE` |
| `Skeleton_Frost_Archmage` | `VOID` | `VOID, ICE` |
| `Skeleton_Frost_Fighter` | `VOID` | `VOID, ICE` |
| `Skeleton_Frost_Knight` | `VOID` | `VOID, ICE` |
| `Skeleton_Frost_Mage` | `VOID` | `VOID, ICE` |
| `Skeleton_Frost_Ranger` | `VOID` | `VOID, ICE` |
| `Skeleton_Frost_Scout` | `VOID` | `VOID, ICE` |
| `Skeleton_Frost_Soldier` | `VOID` | `VOID, ICE` |
| `Skeleton_Incandescent_Fighter` | `VOID` | `VOID` |
| `Skeleton_Incandescent_Footman` | `VOID` | `VOID` |
| `Skeleton_Incandescent_Head` | `VOID` | `VOID` |
| `Skeleton_Incandescent_Mage` | `VOID` | `VOID` |
| `Skeleton_Knight` | `VOID` | `VOID` |
| `Skeleton_Mage` | `VOID` | `VOID` |
| `Skeleton_Pirate_Captain` | `VOID` | `VOID` |
| `Skeleton_Pirate_Gunner` | `VOID` | `VOID` |
| `Skeleton_Pirate_Striker` | `VOID` | `VOID` |
| `Skeleton_Ranger` | `VOID` | `VOID` |
| `Skeleton_Sand_Archer` | `VOID` | `VOID, FIRE` |
| `Skeleton_Sand_Archmage` | `VOID` | `VOID, FIRE` |
| `Skeleton_Sand_Assassin` | `VOID` | `VOID, FIRE` |
| `Skeleton_Sand_Guard` | `VOID` | `VOID, FIRE` |
| `Skeleton_Sand_Mage` | `VOID` | `VOID, FIRE` |
| `Skeleton_Sand_Ranger` | `VOID` | `VOID, FIRE` |
| `Skeleton_Sand_Scout` | `VOID` | `VOID, FIRE` |
| `Skeleton_Sand_Soldier` | `VOID` | `VOID, FIRE` |
| `Skeleton_Scout` | `VOID` | `VOID` |
| `Skeleton_Soldier` | `VOID` | `VOID` |
| `Skrill` | `WATER` | `WATER` |
| `Skrill_Chick` | `WATER` | `WATER` |
| `Slug_Magma` | `FIRE` | `FIRE` |
| `Snail_Frost` | `ICE` | `ICE` |
| `Snail_Magma` | `FIRE` | `FIRE` |
| `Snake_Cobra` | `FIRE` | `FIRE` |
| `Snake_Marsh` | `WATER` | `WATER` |
| `Snake_Rattle` | `FIRE` | `FIRE` |
| `Snapdragon` | `LIFE` | `LIFE` |
| `Snapjaw` | `WATER` | `WATER` |
| `Spark_Living` | `LIGHTNING` | `LIGHTNING` |
| `Sparrow` | `LIGHTNING` | `LIGHTNING` |
| `Spawn_Void` | `VOID` | `VOID` |
| `Spectre_Void` | `VOID` | `VOID` |
| `Spider` | `VOID` | `VOID` |
| `Spider_Cave` | `VOID` | `VOID` |
| `Spirit_Ember` | `FIRE` | `FIRE` |
| `Spirit_Frost` | `ICE` | `ICE` |
| `Spirit_Root` | `LIFE` | `LIFE` |
| `Spirit_Thunder` | `LIGHTNING` | `LIGHTNING` |
| `Squirrel` | `LIFE` | `LIFE` |
| `Tang_Blue` | `WATER` | `WATER` |
| `Tang_Chevron` | `WATER` | `WATER` |
| `Tang_Lemon_Peel` | `WATER` | `WATER` |
| `Tang_Sailfin` | `WATER` | `WATER` |
| `Tetrabird` | `LIGHTNING` | `LIGHTNING` |
| `Tiger_Sabertooth` | `LIFE` | `LIFE` |
| `Toad_Rhino` | `LIFE` | `LIFE` |
| `Toad_Rhino_Magma` | `FIRE` | `FIRE` |
| `Tortoise` | `LIFE` | `LIFE` |
| `Trillodon` | `WATER` | `WATER` |
| `Trilobite` | `WATER` | `WATER` |
| `Trilobite_Black` | `WATER` | `WATER` |
| `Trork_Brawler` | `NEUTRAL` | `-` |
| `Trork_Chieftain` | `NEUTRAL` | `-` |
| `Trork_Guard` | `NEUTRAL` | `-` |
| `Trork_Hunter` | `NEUTRAL` | `-` |
| `Trork_Mauler` | `NEUTRAL` | `-` |
| `Trork_Sentry` | `NEUTRAL` | `-` |
| `Trork_Shaman` | `NEUTRAL` | `-` |
| `Trork_Warrior` | `NEUTRAL` | `-` |
| `Trout_Rainbow` | `WATER` | `WATER` |
| `Turkey` | `LIFE` | `LIFE` |
| `Turkey_Chick` | `LIFE` | `LIFE` |
| `Vulture` | `LIGHTNING` | `LIGHTNING` |
| `Warthog` | `LIFE` | `LIFE` |
| `Warthog_Piglet` | `LIFE` | `LIFE` |
| `Werewolf` | `VOID` | `VOID` |
| `Whale_Humpback` | `WATER` | `WATER` |
| `Wolf_Black` | `LIFE` | `LIFE` |
| `Wolf_White` | `ICE` | `ICE` |
| `Woodpecker` | `LIGHTNING` | `LIGHTNING` |
| `Wraith` | `VOID` | `VOID` |
| `Yeti` | `ICE` | `ICE` |
| `Zombie` | `VOID` | `VOID` |
| `Zombie_Aberrant` | `VOID` | `VOID` |
| `Zombie_Burnt` | `VOID` | `VOID, FIRE` |
| `Zombie_Frost` | `VOID` | `VOID, ICE` |
| `Zombie_Sand` | `VOID` | `VOID, FIRE` |
