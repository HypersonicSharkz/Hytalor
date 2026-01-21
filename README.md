
# Hytalor - Asset Patcher

Hytalor is a light-weight asset patching framework designed to reduce mod conflicts by allowing multiple plugins to modify the same base game asset, without overwriting each other.

Instead of overwriting entire JSON files, Hytalor allows smaller patches which are merged together into a final asset at runtime. 

## 🛠 Status
Hytalor is still under development, and doesn't support Build type assets yet (These mainly being NPC roles)

Road Map:
- More list searching methods, such as Regex or Key-Value checks
- Automatically detect differences of entire Asset overrides and dynamically create patches instead. (Might be impossible)

## ✨ Key Features
* **Conflict-Mitigation**\
  Multiple plugins can modify the same asset without overwriting each other!

* **JSON Patching**\
  Patching through JSON files.

* **Hot-reloading**\
  Changes to patch files are automatically resolved and applied when saved, like any other asset!

* **Patch Operations (****\_op****)**\
  Supports `merge`, `replace`, `add`, and `remove` operations on list elements!


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
Each patch file targets a single base asset:

```json
{
  "BaseAssetPath": "Weathers/Zone1/Zone1_Sunny",
  "Stars": "Sky/Void.png",
  "Clouds": [
    {
      "_index": 2,
      "Colors": [
        {
          "_index": 2,
          "Color": "#FF0000e6"
        }
      ]
    }
  ]
}
```

### What this does
- Targets `Weathers/Zone1/Zone1_Sunny`
- Overrides the `Stars` texture
- Modifies `Clouds[2].Colors[2].Color`
- Leaves everything else untouched.


## 🔧 Array Operations

Arrays are modified using `_index` and optional `_op` fields.

| Operation           | Description                                  |
| ------------------- | -------------------------------------------- |
| `merge` *(default)* | Merge provided fields into the element at `_index`      |
| `replace`           | Replace the entire element at `_index`       |
| `add`               | Insert a new element (or append if no index) |
| `remove`            | Remove the element at `_index`               |

> [!IMPORTANT]  
> The _index refers to the index in the base asset ALWAYS, meaning that the index is automatically updated to correctly point to where the base element is.
> If the base index was removed by a previous patch, `merge`, `replace`, and `remove` does nothing. (Subject to change)

### Example: Add
```json
{
  "BaseAssetPath": "Weathers/Zone1/Zone1_Sunny",
  "Clouds": [
    {
      "_index": 0,
      "Colors": [
        {
          "_index": 2,
          "_op": "add",
          "Hour": 6,
          "Color": "#FF0000FF"
        }
      ]
    }
  ]
}
```

Will add a new element to the `Colors` array in the `Clouds[0]` element.

---

## 🔁 Merge Rules Summary

- **Primitive values** → overwritten
- **Objects** → merged by key
- **Arrays** → modified via `_index`
- **Nested structures** → handled recursively

Invalid indices or operations are safely ignored.
