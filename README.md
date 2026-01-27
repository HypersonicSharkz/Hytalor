
# Hytalor - Asset Patcher

Hytalor is a light-weight asset patching framework designed to reduce mod conflicts by allowing multiple plugins to modify the same base game asset, without overwriting each other.

Instead of overwriting entire JSON files, Hytalor allows smaller patches which are merged together into a final asset at runtime. 

## ✨ Key Features
- **Conflict-Mitigation**\
  Multiple mods can modify the same asset without ovewriting each other.
  
- **Wildcard Asset Targeting**\
  Apply a single Patch to many assets using wildcards.
  
- **Smart Array Selection**\
  Modify array elements using an index or special queries.
  
- **Array Patch Operations**\
  Supports merge, replace, add, and remove operations on arrays.
  
- **Hot-Reloading**\
  Changes to patch files are automatically resolved and applied when saved, like any other asset.


## 📦 Usage/Examples

The patch files must be placed inside a directory:
`Server/Patch/` in your assetpack.

For example
```text
YourPlugin
├── manifest.json
└── Server
    └── Patch
        └── YourPatch.json
```

### 🧩 Patch Files
Each patch file targets one asset by specifying the entire path, or multiple assets by using wildcards.
These Assets can also be from other mods. If a target asset is overwritten by another mod, this version will be used as the "base" asset for patching.

```json
{
  "BaseAssetPath": "Weathers/Zone1/Zone1_Sunny",
  "Stars": "Sky/Void.png",
}
```
Only overrides the `Stars` texture in the Zone1_Sunny asset.

---

```json
{
  "BaseAssetPath": "Weathers/Zone1/*",
  "Stars": "Sky/Void.png",
}
```

Overrides the `Stars` texture of every asset insde the Weathers/Zone1 directory

## 🔧 Array Patching
Hytalor recursivly merges JSON assets. Objects are merged by key, while arrays are modified using special control fields.

| Field               | Description                                  |
| ------------------- | -------------------------------------------- |
| `_index`            | Select array element by index                |
| `_find`             | Selects **first** element matching a query   |
| `_findAll`          | Selects **all** elements matching a query    |
| `_op`               | The operation to apply at the matches element(s) |


| Operation           | Description                                  |
| ------------------- | -------------------------------------------- |
| `merge` *(default)* | Merge provided fields into the element     |
| `replace`           | Replace the entire element       |
| `add`               | Insert a new element (or append to end if no index / find) |
| `addBefore` | Inserts element before `_index` or matched elements (or adds to front if no index / find) |
| `addAfter` | Inserts element after `_index` or matched elements (or append to end if no index / find) |
| `remove`            | Remove the element               |

> [!IMPORTANT]  
> The _index refers to the index at the time of the patch being applied, meaning that it may point to somewhere else if another patch happens before it.
> It is therefore advised to use `_find` queries instead if order is very important.

### Example: Merge
When merging, only the provided fields are updated, and all other fields remain untouched.
The object is not replace, just partially modified.

📄 Base Asset (Before)
```json
{
  "Clouds": [
    {
      "Texture": "Sky/Clouds/Light_Base.png",
      "Speed": 0.5,
      "Opacity": 0.8
    }
  ]
}
```

🧩 Hytalor Patch
```json
{
  "BaseAssetPath": "Weathers/Zone1/Zone1_Sunny",
  "Clouds": [
    {
      "_index": 0,
      "_op": "merge",
      "Speed": 0.7
    }
  ]
}
```

✅ Resulting Asset (After)
```json
{
  "Clouds": [
    {
      "Texture": "Sky/Clouds/Light_Base.png",
      "Speed": 0.7,
      "Opacity": 0.8
    }
  ]
}
```

### Patching Primitive Arrays
Primitve arrays are a bit different to Patch, due to it expecting primitive values and not objects.

📄 Base Asset (Before)
```json
{
  //...
  "Categories": [
    {
      "_op": "add",
      "_value": "Furniture.Benches"
    }
  ],
  "Tags": {
    "Type": [
      "Bench"
    ]
  }
  //...
}
```

🧩 Hytalor Patch
Adding to the end of the array is simple:
```json
{
  "BaseAssetPath": "Item/Items/Bench/Bench_Arcane",
  "Categories": [
    {
      "_op": "add",
      "_value": "Custom.Category"
    }
  ]
}
```

Overwriting the array can be done using the Query system:
```json
{
  "BaseAssetPath": "Item/Items/Bench/Bench_Arcane",
  "$.Tags.Type": [
    "Custom.Type"
  ]
}
```

✅ Resulting Asset (After both patches)
```json
{
  //...
  "Categories": [
    "Furniture.Benches",
    "Custom.Category"
  ],
  "Tags": {
    "Type": [
      "Custom.Type"
    ]
  }
  //...
}
```





### Example: Add
```json
{
  "BaseAssetPath": "NPC/Roles/_Core/Templates/Template_Animal_Neutral",
  "Instructions": [
    {
      "_index": 0,
      "_op": "add",
      "Continue": true,
      "Sensor": {
        "Type": "Any"
      },
      "Actions": [
        {
          "Type": "SpawnParticles",
          "Offset": [
            0,
            1,
            0
          ],
          "ParticleSystem": "Hearts"
        }
      ]
    }
  ]
}
```
#### What this does
- Applies to the `Template_Animal_Neutral` asset.
- Adds new object to `Instructions`, at begging of original array.

Using `addBefore` and removing the `_index` results in the same.
  
---

## `_find` and `_findAll` Query examples.
Hytalor uses **JsonPath** queries to search inside arrays. More info here: (https://github.com/json-path/JsonPath)

For merge operations, direct JsonPath assignment can be used:
```json
{
  "BaseAssetPath": "Weathers/Zone1/*",
  "$.Clouds[*].Colors[?(@.Hour < 12)].Color": "#00EE00"
}
```

The exact same can be achieved using structed JSON as well:

```json
{
  "BaseAssetPath": "Weathers/Zone1/*",
  "Clouds": [
    {
      "_findAll": "$[*]",
      "Colors": [
        {
          "_findAll": "$[?(@.Hour < 12)]",
          "Color": "#00FF00"
        }
      ]
    }
  ]
}
```

#### What this does
- Applies to every weather asset in `Weathers/Zone1/`.
- Selects every cloud
- Then every color with an Hour value below 12
- Sets the color value

---

### Example: Removing
```json
{
  "BaseAssetPath": "Weathers/Zone1/*",
  "Clouds": [
    {
      "_findAll": "$[*]",
      "Colors": [
        {
          "_findAll": "$[?(@.Hour < 12)]",
          "_op": "remove"
        }
      ]
    }
  ]
}
```
#### What this does
- Applies to every weather asset in `Weathers/Zone1/`.
- Selects every cloud
- Removes every color element with an Hour value below 12

---

### Example: Adding
```json
{
  "BaseAssetPath": "Weathers/Zone1/*",
  "Clouds": [
    {
      "_findAll": "$[*]",
      "Colors": [
        {
          "_find": "$[?(@.Hour > 14)]"
          "_op": "add",
          "Hour": 14,
          "Color": "#00FF00"
        }
      ]
    }
  ]
}
```
#### What this does
- Applies to every weather asset in `Weathers/Zone1/`.
- Selects every cloud
- Finds first color with Hour > 14
- Adds new object before the found element

---

## 🛠 Road Map
- Use value from an asset when assigning a new value, or when querying.
- Creating patches through code
