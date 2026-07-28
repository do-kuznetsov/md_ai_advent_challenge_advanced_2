# Day 6 Data Sources

## Source Files

SQL dumps are stored locally in `ai_training/origin/`.

These files are ignored by Git through `.gitignore`:

```gitignore
ai_training/origin/*.sql
```

Available dumps:

- `u0492853_enot_db.sql` - production dump.
- `u0492853_enot_test.sql` - test dump.

## Schema Check

Both dumps have the same table list and the same column definitions.

Tables:

- `e_additive_danger`
- `e_additive_name`
- `products_composition`
- `products_description`
- `products_energy_composition`
- `products_name`
- `products_shelf_life`
- `products_storage_conditions`
- `products_weight`
- `uhtt_products_name`
- `unknown_barcodes`

The dumps are not fully identical at index level:

- production product tables mostly use `UNIQUE_DATA` composite indexes;
- test product tables use a mix of `unique_index` composite indexes and `PRIMARY KEY (barcode)`;
- `products_description` has a unique index in production and no matching index in test.

Conclusion: table and column structure is compatible for dataset extraction. Index structure differs, so the dumps are not strictly identical database schemas.

## Useful Tables For This Task

- `products_composition` - raw product compositions; primary source for dataset examples.
- `products_name` - product names by barcode; useful for richer prompts.
- `e_additive_name` - additive code to human-readable name.
- `e_additive_danger` - additive code to danger score.
- `products_description`, `products_energy_composition`, `products_weight` - optional product context.

## Data Snapshot

Parsed row counts for task-relevant tables:

| Table | Production dump | Test dump |
|---|---:|---:|
| `e_additive_danger` | 218 | 218 |
| `e_additive_name` | 218 | 218 |
| `products_composition` | 914 | 777 |
| `products_name` | 4591 | 798 |
| `products_description` | 857 | 794 |

Composition quality snapshot:

| Metric | Production dump | Test dump |
|---|---:|---:|
| Non-empty compositions | 914 | 777 |
| Unique composition barcodes | 914 | 777 |
| Median composition length | 149 | 146 |
| Max composition length | 4695 | 4695 |
| Compositions with explicit E-code pattern | 186 | 159 |
| Composition barcodes with product name in `products_name` | 892 | 754 |

## Selected Source

Use `u0492853_enot_db.sql` for the next dataset step.

Reason:

- it has more real composition records for dataset selection;
- it has slightly better product-name coverage for composition barcodes;
- additive reference tables match production by row count and columns;
- SQL dumps stay local and ignored, so no source database content enters commits.

Test dump can remain a secondary cross-check source for schema drift and extraction-script validation.
