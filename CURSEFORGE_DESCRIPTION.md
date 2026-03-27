<h1>Socket Reforge — Weapon & Armor Refinement for Hytale</h1>

<p><strong>Upgrade, enhance, socket, and risk it all.</strong> Socket Reforge brings a deep RPG equipment refinement system to Hytale, letting players upgrade their weapons and armor through a risk-reward reforging mechanic, punch sockets into equipment, and enhance them with powerful essences — all with configurable rates, sound effects, and custom UIs.</p>
<p><strong>Latest update highlights:</strong> Socket Expander/Diffuser support items, spellbooks now count as staffs for resonance, and chest loot can roll expander/diffuser supports. Socket Bench previews now reflect expander/diffuser max changes.</p>

<hr>

<h2>⚔️ Weapon & Armor Reforging</h2>

<p>Craft <strong>Reforge Bench</strong> from <strong>Workbench</strong>.</p>
<p>Take your equipment to a <strong>Reforge Bench</strong> and spend <strong>Refinement Globs</strong> to attempt upgrades up to <strong>+3 levels</strong>. But be careful — every attempt carries risk!</p>
<p>Take Iron Ore to Salvage Station to get <strong>15x Refinement Globs</strong>.</p>

<h3>Upgrade Tiers — Weapons</h3>

<table>
  <tr>
    <th>Level</th>
    <th>Name</th>
    <th>Damage Bonus</th>
  </tr>
  <tr>
    <td>+0</td>
    <td>Base</td>
    <td>—</td>
  </tr>
  <tr>
    <td>+1</td>
    <td>★ Sharp</td>
    <td>+10%</td>
  </tr>
  <tr>
    <td>+2</td>
    <td>★★ Deadly</td>
    <td>+15%</td>
  </tr>
  <tr>
    <td>+3</td>
    <td>★★★ Legendary</td>
    <td>+25%</td>
  </tr>
</table>

<h3>Upgrade Tiers — Armor</h3>

<table>
  <tr>
    <th>Level</th>
    <th>Name</th>
    <th>Defense Bonus</th>
  </tr>
  <tr>
    <td>+0</td>
    <td>Base</td>
    <td>—</td>
  </tr>
  <tr>
    <td>+1</td>
    <td>★ Reinforced</td>
    <td>+5%</td>
  </tr>
  <tr>
    <td>+2</td>
    <td>★★ Fortified</td>
    <td>+10%</td>
  </tr>
  <tr>
    <td>+3</td>
    <td>★★★ Impenetrable</td>
    <td>+15%</td>
  </tr>
</table>

<h3>How It Works</h3>

<p>Each reforge attempt rolls one of <strong>four outcomes</strong>:</p>

<ul>
  <li><strong>⬆ Upgrade</strong> — Weapon gains a level</li>
  <li><strong>🎰 Jackpot</strong> — Weapon gains TWO levels!</li>
  <li><strong>➡ No Change</strong> — Nothing happens, material consumed</li>
  <li><strong>⬇ Degrade</strong> — Weapon drops a level</li>
  <li><strong>💥 Shatter</strong> — Weapon is destroyed!</li>
</ul>

<p>Higher upgrade levels have <strong>harder odds</strong> and <strong>higher break chances</strong>, making +3 Legendary weapons truly rare and valuable.</p>

<h3>Reforge Odds — Weapons</h3>

<table>
  <tr>
    <th>Transition</th>
    <th>Upgrade</th>
    <th>Jackpot</th>
    <th>No Change</th>
    <th>Degrade</th>
    <th>Break</th>
  </tr>
  <tr>
    <td>+0 → +1</td>
    <td>34%</td>
    <td>1%</td>
    <td>65%</td>
    <td>0%</td>
    <td>1%</td>
  </tr>
  <tr>
    <td>+1 → +2</td>
    <td>19%</td>
    <td>1%</td>
    <td>45%</td>
    <td>35%</td>
    <td>5%</td>
  </tr>
  <tr>
    <td>+2 → +3</td>
    <td>9.5%</td>
    <td>0.5%</td>
    <td>30%</td>
    <td>60%</td>
    <td>7.5%</td>
  </tr>
</table>

<h3>Reforge Odds — Armor</h3>

<table>
  <tr>
    <th>Transition</th>
    <th>Upgrade</th>
    <th>Jackpot</th>
    <th>No Change</th>
    <th>Degrade</th>
    <th>Break</th>
  </tr>
  <tr>
    <td>+0 → +1</td>
    <td>34%</td>
    <td>1%</td>
    <td>50%</td>
    <td>0%</td>
    <td>10%</td>
  </tr>
  <tr>
    <td>+1 → +2</td>
    <td>19%</td>
    <td>1%</td>
    <td>45%</td>
    <td>25%</td>
    <td>5%</td>
  </tr>
  <tr>
    <td>+2 → +3</td>
    <td>9.5%</td>
    <td>0.5%</td>
    <td>40%</td>
    <td>40%</td>
    <td>2%</td>
  </tr>
</table>

<p><em>*All rates are fully configurable via JSON config files!*</em></p>
<p><em>*Refinement support:</em> <strong>Tool_Hammer_Iron</strong> reduces break chance (5% durability per use). <strong>Tool_Hammer_Thorium</strong> reduces break chance further (15% durability per use). Both are consumed at 0 durability.</p>

<hr>

<h2>🔷 Socket Punching System</h2>

<p>Use a <strong>Socket Punch Bench</strong> to punch sockets into your equipment! Sockets can be filled with powerful essences to gain additional stat bonuses. Inspired by Path of Exile's socket system.</p>

<h3>How Socket Punching Works</h3>

<ol>
  <li>Place your equipment (weapon or armor) in the bench</li>
  <li>Add a <strong>Socket Puncher</strong> material (required)</li>
  <li>Optionally add a <strong>Socket Stabilizer</strong> to reduce break chance</li>
  <li>Click to punch — success adds a socket, failure may destroy the item!</li>
</ol>

<h3>Socket Limits</h3>

<table>
  <tr>
    <th>Equipment Type</th>
    <th>Max Sockets</th>
  </tr>
  <tr>
    <td>Weapons</td>
    <td>4</td>
  </tr>
  <tr>
    <td>Armor</td>
    <td>4</td>
  </tr>
</table>

<h3>How to Craft</h3>

<p><strong>Socket Punch Bench</strong> — Craft at Workbench (Tier 2):</p>
<ul>
  <li>8 Iron Bars + 3 Emeralds</li>
</ul>

<p><strong>Socket Puncher</strong> — Craft at Salvage Bench:</p>
<ul>
  <li>1 Iron Bar → 15 Socket Punchers</li>
</ul>

<p><strong>Socket Stabilizer</strong> — Craft at Alchemy Bench (Tier 1):</p>
<ul>
  <li>15 Socket Punchers + 1 Emerald Gem</li>
  <li>Provides +15% success chance and -5% break chance</li>
</ul>

<p><em>*Bonus 5th socket: 1% chance when punching the 4th socket!*</em></p>

<h3>Socket Punching Odds</h3>

<table>
  <tr>
    <th>Socket #</th>
    <th>Success Chance</th>
    <th>Break Chance</th>
  </tr>
  <tr>
    <td>1st Socket</td>
    <td>90%</td>
    <td>2%</td>
  </tr>
  <tr>
    <td>2nd Socket</td>
    <td>75%</td>
    <td>5%</td>
  </tr>
  <tr>
    <td>3rd Socket</td>
    <td>55%</td>
    <td>10%</td>
  </tr>
  <tr>
    <td>4th Socket</td>
    <td>35%</td>
    <td>18%</td>
  </tr>
</table>

<p><em>*Each additional socket is harder to obtain with higher risk!*</em></p>

<hr>

<h2>💎 Essence System</h2>

<p>Fill your sockets with <strong>Essences</strong> to gain powerful stat bonuses! Each essence type provides unique effects based on its element and tier.</p>

<h3>Essence Types</h3>

<table>
  <tr>
    <th>Type</th>
    <th>Theme</th>
    <th>Primary Effects</th>
  </tr>
  <tr>
    <td>🔥 <strong>Fire</strong></td>
    <td>Burns with intense heat</td>
    <td>Damage, Fire Damage</td>
  </tr>
  <tr>
    <td>❄️ <strong>Ice</strong></td>
    <td>Glows with frost</td>
    <td>Cold Damage, Slow Effect</td>
  </tr>
  <tr>
    <td>⚡ <strong>Lightning</strong></td>
    <td>Crackles with energy</td>
    <td>Attack Speed, Crit Chance, Evasion (Armor)</td>
  </tr>
  <tr>
    <td>💚 <strong>Life</strong></td>
    <td>Pulses with vitality</td>
    <td>Health, Life Steal</td>
  </tr>
  <tr>
    <td>🌑 <strong>Void</strong></td>
    <td>Shimmers with darkness</td>
    <td>Crit Damage</td>
  </tr>
  <tr>
    <td>💧 <strong>Water</strong></td>
    <td>Flows with clarity</td>
    <td>Damage (Weapon), Regeneration (Armor)</td>
  </tr>
</table>

<h3>Essence Tiers</h3>

<p>Essences gain power based on <strong>consecutive count</strong> of the same type. For example: Life, Life, Life, Fire = Tier 3 Life, Tier 1 Fire. Higher tiers provide stronger effects!</p>

<table>
  <tr>
    <th>Essence Type</th>
    <th>Tier 1</th>
    <th>Tier 3</th>
    <th>Tier 5</th>
  </tr>
  <tr>
    <td><strong>Fire</strong></td>
    <td>+2% Damage, +3 Flat DMG</td>
    <td>+6% Damage, +8 Flat DMG</td>
    <td>+12% Damage, +15 Flat DMG</td>
  </tr>
  <tr>
    <td><strong>Ice</strong></td>
    <td>+2% Slow, +2 Cold DMG</td>
    <td>+5% Slow, +6 Cold DMG</td>
    <td>+5% Freeze, +12 Cold DMG</td>
  </tr>
  <tr>
    <td><strong>Lightning</strong></td>
    <td>Weapon: +3% ATK Speed, +2% Crit<br>Armor: +1% Evasion</td>
    <td>Weapon: +7% ATK Speed, +4% Crit<br>Armor: +3% Evasion</td>
    <td>Weapon: +15% ATK Speed, +8% Crit<br>Armor: +5% Evasion</td>
  </tr>
  <tr>
    <td><strong>Life</strong> (Weapon)</td>
    <td>+2% Lifesteal</td>
    <td>+5% Lifesteal</td>
    <td>+10% Lifesteal</td>
  </tr>
  <tr>
    <td><strong>Life</strong> (Armor)</td>
    <td>+10 HP</td>
    <td>+25 HP</td>
    <td>+50 HP</td>
  </tr>
  <tr>
    <td><strong>Void</strong></td>
    <td>+5% Crit DMG</td>
    <td>+12% Crit DMG</td>
    <td>+25% Crit DMG</td>
  </tr>
  <tr>
    <td><strong>Water</strong> (Armor)</td>
    <td>+1 Regeneration</td>
    <td>+3 Regeneration</td>
    <td>+5 Regeneration</td>
  </tr>
</table>

<h3>Available Stats</h3>

<p>Essences can provide bonuses to the following stats:</p>

<ul>
  <li><strong>Offensive:</strong> Attack Speed, Damage, Crit Chance, Crit Damage</li>
  <li><strong>Defensive:</strong> Health, Defense, Evasion</li>
  <li><strong>Utility:</strong> Life Steal, Movement Speed, Luck</li>
  <li><strong>Elemental:</strong> Cold Damage, Slow Effect, Fire Damage, Lightning Damage</li>
</ul>

<h3>Greater Essences</h3>

<p>In addition to regular essences, <strong>Greater (Concentrated) Essences</strong> are available with enhanced power:</p>

<ul>
  <li>Stronger stat bonuses than regular essences</li>
  <li>Available for all 6 essence types</li>
  <li>Craftable through the Essence Socket Bench</li>
</ul>

<h3>How to Craft</h3>

<p><strong>Essence Socket Bench</strong> — Craft at Workbench (Tier 2):</p>
<ul>
  <li>8 Iron Bars + 3 Wood</li>
</ul>

<h3>Essence Management</h3>

<ul>
  <li><strong>Socket Essence:</strong> Place an essence into an empty socket at Essence Socket Bench to gain its effects</li>
  <li><strong>Remove/Clear Essence:</strong> Use <strong>Tool_Hammer_Iron</strong> as support to clear socketed essences</li>
  <li><strong>Clear Roll:</strong> On failure, durability is still reduced and a random socket can break</li>
  <li><strong>Hammer Wear:</strong> Clearing uses 10% hammer durability per process and consumes the hammer at 0 durability</li>
  <li><strong>Repair Broken Sockets:</strong> Use Voidheart to repair broken sockets</li>
</ul>

<hr>

<h2>✨ Resonance System</h2>

<p>Unlock <strong>powerful hidden effects</strong> by socketting specific essence combinations! The Resonance System is inspired by runewords — certain essence orders trigger unique bonuses that aren't available from individual essences alone.</p>

<h3>How Resonance Works</h3>

<ul>
  <li><strong>Fill All Sockets:</strong> Resonance only activates when every socket on your equipment contains an essence</li>
  <li><strong>Order Matters:</strong> The sequence of essences determines which resonance effect you get</li>
  <li><strong>Quality Tiers:</strong> Higher socket counts can unlock rarer resonance qualities</li>
  <li><strong>Free Power:</strong> Once discovered, resonance effects require no extra materials!</li>
  <li><strong>Multiple Weapons:</strong> Works with swords, daggers, bows, crossbows, and more!</li>
</ul>

<h3>Discover Hidden Combinations</h3>

<p>There are <strong>many resonance effects</strong> waiting to be discovered across different socket counts and weapon types:</p>

<ul>
  <li><strong>On-Crit Effects:</strong> Some resonances trigger powerful effects when you land a critical hit</li>
  <li><strong>On-Hit Effects:</strong> Certain combinations cause effects on every hit</li>
  <li><strong>Chain Effects:</strong> Some resonances chain damage to nearby enemies</li>
  <li><strong>Defensive Bonuses:</strong> Thorns, dodge, and shield effects</li>
  <li><strong>Execute Mechanics:</strong> Deal extra damage to low-health enemies</li>
  <li><strong>Aura Effects:</strong> Burn enemies around you</li>
  <li><strong>Survival:</strong> Chance to cheat death or surge heal</li>
  <li><strong>Elemental:</strong> Lightning strikes, frost novas, and more</li>
</ul>

<p><strong>Example:</strong> A sword with Fire + Lightning + Life essences creates "Kingsbrand" — granting +8% Damage, +6% Crit Chance, +2% Lifesteal, and a chance to call lightning strikes on hit!</p>

<p><em>Hint: Try different essence orders with 3, 4, and 5 sockets! Experiment with different combinations — share your discoveries with the community!</em></p>

<hr>

<h2>💎 Socket Loot System</h2>

<p>Socketed equipment can now be found throughout the game world! No need to punch sockets yourself — find them as loot!</p>

<h3>Loot Sources</h3>

<ul>
  <li><strong>Enemy Drops</strong> — Defeated NPCs have a chance to drop equipment with sockets</li>
  <li><strong>Treasure Chests</strong> — Found chests may contain socketed gear as rewards</li>
</ul>

<h3>Loot Rarity</h3>

<ul>
  <li>Higher socket counts are rarer to find</li>
  <li>Essence-filled sockets are more valuable</li>
  <li>All looted equipment have broken sockets that can be repaired in order to be used</li>
</ul>

<p><em>Explore the world to find the best socketed equipment!</em></p>

<hr>

<h2>🛠️ HyUI Integration</h2>

<p>Socket Reforge now supports <strong>HyUI</strong> for enhanced user interfaces! When HyUI is installed, players get access to improved, responsive UI elements for all reforging and socketing operations.</p>

<ul>
  <li><strong>Optional Dependency</strong> — Works with or without HyUI installed</li>
  <li><strong>Enhanced Equipment List</strong> — Improved equipment selection with real-time filtering</li>
  <li><strong>Better Socket Previews</strong> — Visual socket state display with progress tracking</li>
  <li><strong>Improved Event Handling</strong> — No more UI freezes or client locks</li>
</ul>

<p><em>Get HyUI from <a href="https://www.curseforge.com/hytale/mods/hyui">CurseForge</a> for the best experience!</em></p>

<hr>

<h2>🔨 Socket Management Tools</h2>

<p>New tools for managing your socketed equipment!</p>

<h3>Iron Building Hammer</h3>

<p>Manage socketed essences from your equipment with <strong>Iron Building Hammer</strong>:</p>

<ul>
  <li>Clears socketed essences without deleting the socket layout</li>
  <li>Useful for re-configuring your build</li>
  <li>Failure can break a random socket</li>
  <li>Hammer durability is consumed on each clear process</li>
</ul>

<hr>

<h2>❤️ Regeneration & Enhanced Effects</h2>

<p>Essences now provide additional benefits beyond combat stats!</p>

<h3>Regeneration System</h3>

<ul>
  <li><strong>Life Essence Armor</strong> — Provides Max HP increase</li>
  <li><strong>Water Essence</strong> — Grants natural regeneration benefits</li>
  <li>Stack multiple Life essences for increased regen rates</li>
</ul>

<h3>Extended Tier 5 Effects</h3>

<p>Tier 5 essences now have enhanced effects on both weapons AND armor:</p>

<table>
  <tr>
    <th>Essence</th>
    <th>Weapon T5 Effect</th>
    <th>Armor T5 Effect</th>
  </tr>
  <tr>
    <td><strong>Fire</strong></td>
    <td>+12% Damage, +15 Fire DMG</td>
    <td>+15% Fire DMG, Fire Reflection</td>
  </tr>
  <tr>
    <td><strong>Ice</strong></td>
    <td>+5% Freeze, +12 Cold DMG</td>
    <td>+5% Freeze, +12 Cold DMG</td>
  </tr>
  <tr>
    <td><strong>Life</strong></td>
    <td>+10% Lifesteal</td>
    <td>+50 HP, +5 HP/sec Regen</td>
  </tr>
  <tr>
    <td><strong>Void</strong></td>
    <td>+25% Crit Damage + Blood Pact (HP sacrifice adds bonus damage)</td>
    <td>+25% Crit Damage</td>
  </tr>
  <tr>
    <td><strong>Water</strong></td>
    <td>N/A (weapons)</td>
    <td>Regeneration bonus</td>
  </tr>
</table>
<p><em>Armor identity prefixes at Tier 5:</em> Fire armor uses <strong>Infernal</strong> prefix and Ice armor uses <strong>Glacial</strong> prefix.</p>

<h2>🔐 Server Commands (OP Only)</h2>

<ul>
  <li><code>/socketpunch</code> — Open Socket Punch bench UI directly</li>
  <li><code>/essence</code> — Open Essence Socket bench UI directly</li>
  <li><code>/rfadmin refine &lt;0-3&gt;</code> — Set held item refinement level</li>
  <li><code>/rfadmin sockets &lt;current&gt; [max]</code> — Set held item sockets</li>
  <li><code>/rfadmin addmax &lt;amount&gt;</code> — Increase held item max sockets</li>
</ul>

<hr>

<h2>🔧 Fully Configurable</h2>

<p>Everything is customizable through auto-generated JSON config files:</p>

<h3>RefinementConfig.json</h3>
<ul>
  <li>Damage multipliers per weapon upgrade level</li>
  <li>Defense multipliers per armor upgrade level</li>
  <li>Break chances for weapons and armor</li>
  <li>Reforge outcome weights (degrade/same/upgrade/jackpot) for each level</li>
</ul>

<h3>SocketConfig.json</h3>
<ul>
  <li>Maximum sockets for weapons and armor</li>
  <li>Socket punch success chances per socket count</li>
  <li>Socket punch break chances per socket count</li>
  <li>Essence clear/removal success chance behavior</li>
</ul>

<h3>SFXConfig.json</h3>
<ul>
  <li>Sound effects for every reforge event (start, success, jackpot, fail, no change, shatter)</li>
  <li>Valid reforge bench block IDs</li>
</ul>

<hr>

<h2>📦 Installation</h2>

<h3>Install the Mod</h3>

<ol>
  <li>Copy the mod JAR to your Hytale server's <code>mods</code> folder</li>
  <li>Start the server — config files generate automatically</li>
  <li>The mod will create:
    <ul>
      <li><code>mods/irai.mod.reforge_SocketReforge/RefinementConfig.json</code></li>
      <li><code>mods/irai.mod.reforge_SocketReforge/SocketConfig.json</code></li>
      <li><code>mods/irai.mod.reforge_SocketReforge/SFXConfig.json</code></li>
    </ul>
  </li>
</ol>

<hr>

<h2>✨ Features at a Glance</h2>

<ul>
  <li><strong>Risk-reward reforging</strong> with 4 outcomes + shatter for both weapons and armor</li>
  <li><strong>3 upgrade tiers</strong> with unique names and star ratings for weapons and armor</li>
  <li><strong>Socket punching system</strong> — add up to 4-5 sockets to weapons and armor</li>
  <li><strong>6 essence types</strong> (Fire, Ice, Lightning, Life, Void, Water) with tier-based effects</li>
  <li><strong>Greater Essences</strong> — Concentrated higher-tier essence variants</li>
  <li><strong>Consecutive tier system</strong> — stack same-type essences for higher bonuses</li>
  <li><strong>Resonance System</strong> — discover hidden runeword-like effects by finding specific essence combinations</li>
  <li><strong>Socket Loot System</strong> — enemies and chests can now drop socketed equipment!</li>
  <li><strong>Average Damage Tooltips</strong> — see true weapon damage output in tooltips</li>
  <li><strong>Extended T5 effects</strong> — includes Void Blood Pact bonus-damage behavior and armor prefixes</li>
  <li><strong>Regeneration system</strong> — Life and Water essences grant HP regen on armor</li>
  <li><strong>Iron Building Hammer support</strong> — used in both Essence and Reforge benches with durability wear</li>
  <li><strong>DynamicTooltips compatibility</strong> — socket and essence info displayed as in-game tooltips<br><em>(Requires <a href="https://www.curseforge.com/hytale/mods/dynamictooltipslib">DynamicTooltipsLib</a>)</em></li>
  <li><strong>HyUI Support</strong> — Optional enhanced UI when HyUI mod is installed</li>
  <li><strong>HyUI fallback safety</strong> — bench interactions remain usable if HyUI is unavailable</li>
  <li><strong>ECS damage & defense system</strong> that applies multipliers in real-time combat</li>
  <li><strong>Bench preview UIs</strong> with detailed refinement/socket outcome information</li>
  <li><strong>Socket Punching UI</strong> for adding sockets with risk/reward mechanics</li>
  <li><strong>Essence Socketing UI</strong> for managing socketed essences</li>
  <li><strong>Configurable sound effects</strong> for every reforge event</li>
  <li><strong>Smart item detection</strong> via item ID, categories, and structure checks</li>
  <li><strong>Metadata-based refinement</strong> — no item duplication or patching required</li>
  <li><strong>Fully configurable</strong> rates, weights, multipliers, and sounds via JSON</li>
</ul>

<hr>

<h2>🎮 For Server Operators</h2>

<p>Socket Reforge is designed to be <strong>drop-in and configurable</strong>. Default values provide a balanced experience out of the box, but every rate can be tuned:</p>

<ul>
  <li>Want easier upgrades? Increase upgrade weights and lower break chances</li>
  <li>Want a hardcore experience? Crank up degrade and shatter rates</li>
  <li>Want easier socketing? Increase success chances and reduce break rates</li>
  <li>Custom weapons? Just follow the categorization Category: Items.Weapon</li>
</ul>

<hr>

<h2>🗺️ Roadmap</h2>

<p>Here's what's planned for future updates:</p>

<table>
  <tr>
    <th>Feature</th>
    <th>Description</th>
    <th>Status</th>
  </tr>
  <tr>
    <td><strong>Refinement</strong></td>
    <td>Weapons and Armor upgrade system</td>
    <td>✅ Available</td>
  </tr>
  <tr>
    <td><strong>Socket Punching</strong></td>
    <td>Add sockets to equipment for essence insertion</td>
    <td>✅ Available</td>
  </tr>
  <tr>
    <td><strong>Essence Socketing</strong></td>
    <td>Socket essences for stat bonuses (Fire, Ice, Lightning, Life, Void, Water)</td>
    <td>✅ Available</td>
  </tr>
  <tr>
    <td><strong>Supporting Materials</strong></td>
    <td>Additional materials for socket punching bonuses</td>
    <td>✅ Available</td>
  </tr>
  <tr>
    <td><strong>Essence Crafting</strong></td>
    <td>Craft and upgrade essences</td>
    <td>✅ Available</td>
  </tr>
  <tr>
    <td><strong>Lore System</strong></td>
    <td>Spirit gems, lore sockets, and combat abilities tied to wild spirits</td>
    <td>⚠️ WIP</td>
  </tr>
   <tr>
    <td><strong>Gem Socketing</strong></td>
    <td>Craft and upgrade essences</td>
    <td>✅ WIP</td>
  </tr>
</table>

<hr>

<h2>🔗 Links</h2>
<p>Found any bugs? please post it here: https://legacy.curseforge.com/hytale/mods/socket-reforge/settings/issues</p>
<ul>
  <li><strong>Source:</strong> <a href="https://github.com/Rizrokkz">GitHub</a></li>
  <li><strong>Test Video:</strong> <a href="https://www.youtube.com/watch?v=QZYTjpr7mms">YouTube</a></li>
  <li><strong>Discord:</strong> <a href="https://discord.gg/ATXAqrKGF4">Discord</a></li>
  <li><strong>Support:</strong> <a href="https://ko-fi.com/P5P41T8LCR"><img src="https://ko-fi.com/img/githubbutton_sm.svg" alt="ko-fi"></a></li>
</ul>

<hr>

<p><em>Made by iRaiden</em></p>
