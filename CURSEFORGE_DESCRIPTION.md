<h1>Socket Reforge — Weapon & Armor Refinement for Hytale</h1>

<p><strong>Upgrade, enhance, and risk it all.</strong> Socket Reforge brings a deep RPG equipment refinement system to Hytale, letting players upgrade their weapons and armor through a risk-reward reforging mechanic with configurable rates, sound effects, and a custom stats UI.</p>

<hr>

<h2>⚔️ Weapon & Armor Reforging</h2>

<p>Take your equipment to a <strong>Reforge Bench</strong> and spend <strong>Iron Bars</strong> to attempt upgrades up to <strong>+3 levels</strong>. But be careful — every attempt carries risk!</p>

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

<hr>

<h2>📊 Weapon Stats UI</h2>

<p>Use <code>/weaponstats</code> to open a custom in-game UI panel showing:</p>

<ul>
  <li>Current weapon name and upgrade level</li>
  <li>Damage multiplier with progress bar</li>
  <li>Next level damage preview</li>
  <li>Reforge outcome probabilities</li>
  <li>Max level comparison</li>
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

<h3>SFXConfig.json</h3>
<ul>
  <li>Sound effects for every reforge event (start, success, jackpot, fail, no change, shatter)</li>
  <li>Valid reforge bench block IDs</li>
</ul>

<hr>

<h2>🛠️ Commands</h2>

<table>
  <tr>
    <th>Command</th>
    <th>Description</th>
  </tr>
  <tr>
    <td><code>/patchassets</code></td>
    <td>Patch and register weapons and armor from Assets.zip, folders, and mod JARs</td>
  </tr>
  <tr>
    <td><code>/weaponstats</code></td>
    <td>Open the Weapon Stats UI for your held weapon</td>
  </tr>
  <tr>
    <td><code>/checkname</code></td>
    <td>Check the translation name of your held item</td>
  </tr>
</table>

<hr>

<h2>📦 Installation</h2>

<h3>Step 1: Install the Mod</h3>

<ol>
  <li>Copy the mod JAR to your Hytale server's <code>mods</code> folder</li>
  <li>Start the server — config files generate automatically</li>
  <li>The mod will create:
    <ul>
      <li><code>mods/irai.mod.reforge_SocketReforge/RefinementConfig.json</code></li>
      <li><code>mods/irai.mod.reforge_SocketReforge/SFXConfig.json</code></li>
    </ul>
  </li>
</ol>

<h3>Step 2: Patch Your Items (Important!)</h3>

<p>Before players can reforge items, you must patch them:</p>

<ol>
  <li>Run <code>/patchassets</code> ingame first time — this creates <code>assets_path.txt</code></li>
  <li>Edit <code>assets_path.txt</code> to provide the file locations:
    <ul>
      <li>Path to the game's <code>Assets.zip</code></li>
      <li>Path to the server's <code>mods</code> folder</li>
    </ul>
  </li>
  <li>Run <code>/patchassets</code> ingame again to scan and generate upgrade variants</li>
  <li>Restart the server once patching is complete</li>
</ol>

<p>This command scans:</p>
<ul>
  <li><code>assets.zip</code> (base game items)</li>
  <li>Mod JARs in the <code>mods</code> folder</li>
</ul>

<p>And generates upgrade variants for all weapons and armor found.</p>

<h3>Step 3: Configure</h3>

<p>Edit <code>RefinementConfig.json</code> to customize:</p>
<ul>
  <li>Damage multipliers for weapons</li>
  <li>Defense multipliers for armor</li>
  <li>Break chances</li>
  <li>Reforge odds</li>
</ul>

<h3>Step 4: Restart</h3>

<p>Restart the server to apply all changes.</p>

<hr>

<h2>✨ Features at a Glance</h2>

<ul>
  <li><strong>Risk-reward reforging</strong> with 4 outcomes + shatter for both weapons and armor</li>
  <li><strong>3 upgrade tiers</strong> with unique names and star ratings for weapons and armor</li>
  <li><strong>ECS damage & defense system</strong> that applies multipliers in real-time combat</li>
  <li><strong>Custom Stats UI</strong> with detailed upgrade information for weapons and armor</li>
  <li><strong>Configurable sound effects</strong> for every reforge event</li>
  <li><strong>Auto-save</strong> every 5 minutes with data persistence</li>
  <li><strong>Item display sync</strong> across all players every 30 seconds</li>
  <li><strong>Smart item detection</strong> via item ID, categories, and structure checks</li>
  <li><strong>Asset patcher</strong> to register weapons and armor from Assets.zip and mod JARs</li>
  <li><strong>Fully configurable</strong> rates, weights, multipliers, and sounds via JSON</li>
</ul>

<hr>

<h2>🎮 For Server Operators</h2>

<p>Socket Reforge is designed to be <strong>drop-in and configurable</strong>. Default values provide a balanced experience out of the box, but every rate can be tuned:</p>

<ul>
  <li>Want easier upgrades? Increase upgrade weights and lower break chances</li>
  <li>Want a hardcore experience? Crank up degrade and shatter rates</li>
  <li>Custom weapons? Just follow the <code>Weapon_</code> naming convention and run <code>/patchassets</code></li>
</ul>

<hr>

<h2>🗺️ Roadmap</h2>

<p>Here's what's planned for future updates:</p>

<table>
  <tr>
    <th>Feature</th>
    <th>Description</th>
  </tr>
  <tr>
    <td><strong>Refinement</strong></td>
    <td>Weapons and Armor (✓ Now Available!)</td>
  </tr>
  <tr>
    <td><strong>Socket Punching</strong></td>
    <td>Add sockets to equipment for gem insertion</td>
  </tr>
  <tr>
    <td><strong>Gem Socketing</strong></td>
    <td>Weapon enchantments (Fire, Frost, etc.) by adding specific gems + essence</td>
  </tr>
</table>

<hr>

<h2>🔗 Links</h2>

<ul>
  <li><strong>Source:</strong> <a href="https://github.com/Rizrokkz">GitHub</a></li>
  <li><strong>Test Video:</strong> <a href="https://www.youtube.com/watch?v=QZYTjpr7mms">YouTube</a></li>
  <li><strong>Support:</strong> <a href="https://ko-fi.com/P5P41T8LCR"><img src="https://ko-fi.com/img/githubbutton_sm.svg" alt="ko-fi"></a></li>
</ul>

<hr>

<p><em>Made by iRaiden</em></p>
