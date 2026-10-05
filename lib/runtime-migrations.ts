/** Static additive recovery for Sites-managed D1 deployments whose publication
 * does not apply Drizzle migrations. No SQL or table name comes from HTTP input.
 * Canonical scope: migrations 0004 through 0008. Never reads credential/data rows.
 */
export const RUNTIME_SCHEMA_VERSION='trail-0004-0008-v1';
export class RuntimeSchemaError extends Error {
  readonly status=503;
  constructor(){super('Journal storage setup is temporarily unavailable. Your photos remain safe on your device.');this.name='RuntimeSchemaError';}
}
const CREATE_STATEMENTS=[
  "CREATE TABLE IF NOT EXISTS `admin_audit` (\n\t`id` text PRIMARY KEY NOT NULL,\n\t`actor_id` text,\n\t`target_id` text,\n\t`action` text NOT NULL,\n\t`created_at` text NOT NULL\n)",
  "CREATE TABLE IF NOT EXISTS `ai_usage` (\n\t`id` text PRIMARY KEY NOT NULL,\n\t`user_id` text NOT NULL,\n\t`observation_id` text,\n\t`period` text NOT NULL,\n\t`purpose` text NOT NULL,\n\t`model` text NOT NULL,\n\t`input_tokens` integer NOT NULL,\n\t`output_tokens` integer NOT NULL,\n\t`micro_usd` integer NOT NULL,\n\t`created_at` text NOT NULL,\n\tFOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade\n)",
  "CREATE INDEX IF NOT EXISTS `ai_usage_user_period` ON `ai_usage` (`user_id`,`period`)",
  "CREATE TABLE IF NOT EXISTS `avatar_generations` (\n\t`user_id` text NOT NULL,\n\t`period` text NOT NULL,\n\t`used` integer DEFAULT 0 NOT NULL,\n\t`locked_until` integer DEFAULT 0 NOT NULL,\n\t`token` text,\n\tPRIMARY KEY(`user_id`, `period`),\n\tFOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade\n)",
  "CREATE TABLE IF NOT EXISTS `member_access` (\n\t`user_id` text PRIMARY KEY NOT NULL,\n\t`role` text DEFAULT 'member' NOT NULL,\n\t`status` text DEFAULT 'active' NOT NULL,\n\t`updated_at` text NOT NULL,\n\tFOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade\n)",
  "CREATE TABLE IF NOT EXISTS `photo_allowance` (\n\t`observation_id` text PRIMARY KEY NOT NULL,\n\t`user_id` text NOT NULL,\n\t`period` text NOT NULL,\n\t`bonus_year` text,\n\t`created_at` text NOT NULL,\n\tFOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade\n)",
  "CREATE INDEX IF NOT EXISTS `photo_allowance_owner_period` ON `photo_allowance` (`user_id`,`period`)",
  "CREATE TABLE IF NOT EXISTS `publication_checks` (\n\t`observation_id` text PRIMARY KEY NOT NULL,\n\t`content_hash` text NOT NULL,\n\t`decision` text NOT NULL,\n\t`checked_at` text NOT NULL,\n\tFOREIGN KEY (`observation_id`) REFERENCES `observations`(`id`) ON UPDATE no action ON DELETE cascade\n)",
  "CREATE TABLE IF NOT EXISTS `subscriptions` (\n\t`token_hash` text PRIMARY KEY NOT NULL,\n\t`user_id` text NOT NULL,\n\t`token_envelope` text NOT NULL,\n\t`product_id` text NOT NULL,\n\t`plan` text NOT NULL,\n\t`status` text NOT NULL,\n\t`starts_at` text NOT NULL,\n\t`expires_at` text NOT NULL,\n\t`checked_at` integer NOT NULL,\n\t`acknowledged` integer DEFAULT 0 NOT NULL,\n\t`is_trial` integer DEFAULT 0 NOT NULL,\n\tFOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade\n)",
  "CREATE INDEX IF NOT EXISTS `subscriptions_user_expiry` ON `subscriptions` (`user_id`,`expires_at`)",
  "CREATE TABLE IF NOT EXISTS `service_config` (\n\t`key` text PRIMARY KEY NOT NULL,\n\t`envelope` text NOT NULL,\n\t`updated_at` text NOT NULL\n)",
  "CREATE TABLE IF NOT EXISTS `ai_reservations` (\n\t`id` text PRIMARY KEY NOT NULL,\n\t`day_key` text NOT NULL,\n\t`reserved_micro_usd` integer NOT NULL,\n\t`settled_micro_usd` integer,\n\t`created_at` text NOT NULL,\n\t`settled_at` text\n)",
  "CREATE TABLE IF NOT EXISTS `identification_stages` (\n\t`observation_id` text NOT NULL,\n\t`user_id` text NOT NULL,\n\t`stage` text NOT NULL,\n\t`data` text NOT NULL,\n\t`created_at` text NOT NULL,\n\tPRIMARY KEY(`observation_id`, `stage`),\n\tFOREIGN KEY (`observation_id`) REFERENCES `observations`(`id`) ON UPDATE no action ON DELETE cascade,\n\tFOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade\n)",
  "CREATE INDEX IF NOT EXISTS `identification_stages_owner` ON `identification_stages` (`user_id`)"
] as const;
const TABLES=[
  {
    "name": "admin_audit",
    "columns": [
      {
        "name": "id",
        "type": "text",
        "notnull": 1,
        "pk": 1
      },
      {
        "name": "actor_id",
        "type": "text",
        "notnull": 0,
        "pk": 0
      },
      {
        "name": "target_id",
        "type": "text",
        "notnull": 0,
        "pk": 0
      },
      {
        "name": "action",
        "type": "text",
        "notnull": 1,
        "pk": 0
      },
      {
        "name": "created_at",
        "type": "text",
        "notnull": 1,
        "pk": 0
      }
    ],
    "foreignKeys": []
  },
  {
    "name": "ai_usage",
    "columns": [
      {
        "name": "id",
        "type": "text",
        "notnull": 1,
        "pk": 1
      },
      {
        "name": "user_id",
        "type": "text",
        "notnull": 1,
        "pk": 0
      },
      {
        "name": "observation_id",
        "type": "text",
        "notnull": 0,
        "pk": 0
      },
      {
        "name": "period",
        "type": "text",
        "notnull": 1,
        "pk": 0
      },
      {
        "name": "purpose",
        "type": "text",
        "notnull": 1,
        "pk": 0
      },
      {
        "name": "model",
        "type": "text",
        "notnull": 1,
        "pk": 0
      },
      {
        "name": "input_tokens",
        "type": "integer",
        "notnull": 1,
        "pk": 0
      },
      {
        "name": "output_tokens",
        "type": "integer",
        "notnull": 1,
        "pk": 0
      },
      {
        "name": "micro_usd",
        "type": "integer",
        "notnull": 1,
        "pk": 0
      },
      {
        "name": "created_at",
        "type": "text",
        "notnull": 1,
        "pk": 0
      }
    ],
    "foreignKeys": [
      {
        "from": "user_id",
        "table": "users",
        "to": "id",
        "on_delete": "CASCADE"
      }
    ]
  },
  {
    "name": "avatar_generations",
    "columns": [
      {
        "name": "user_id",
        "type": "text",
        "notnull": 1,
        "pk": 1
      },
      {
        "name": "period",
        "type": "text",
        "notnull": 1,
        "pk": 2
      },
      {
        "name": "used",
        "type": "integer",
        "notnull": 1,
        "pk": 0
      },
      {
        "name": "locked_until",
        "type": "integer",
        "notnull": 1,
        "pk": 0
      },
      {
        "name": "token",
        "type": "text",
        "notnull": 0,
        "pk": 0
      }
    ],
    "foreignKeys": [
      {
        "from": "user_id",
        "table": "users",
        "to": "id",
        "on_delete": "CASCADE"
      }
    ]
  },
  {
    "name": "member_access",
    "columns": [
      {
        "name": "user_id",
        "type": "text",
        "notnull": 1,
        "pk": 1
      },
      {
        "name": "role",
        "type": "text",
        "notnull": 1,
        "pk": 0
      },
      {
        "name": "status",
        "type": "text",
        "notnull": 1,
        "pk": 0
      },
      {
        "name": "updated_at",
        "type": "text",
        "notnull": 1,
        "pk": 0
      }
    ],
    "foreignKeys": [
      {
        "from": "user_id",
        "table": "users",
        "to": "id",
        "on_delete": "CASCADE"
      }
    ]
  },
  {
    "name": "photo_allowance",
    "columns": [
      {
        "name": "observation_id",
        "type": "text",
        "notnull": 1,
        "pk": 1
      },
      {
        "name": "user_id",
        "type": "text",
        "notnull": 1,
        "pk": 0
      },
      {
        "name": "period",
        "type": "text",
        "notnull": 1,
        "pk": 0
      },
      {
        "name": "bonus_year",
        "type": "text",
        "notnull": 0,
        "pk": 0
      },
      {
        "name": "created_at",
        "type": "text",
        "notnull": 1,
        "pk": 0
      }
    ],
    "foreignKeys": [
      {
        "from": "user_id",
        "table": "users",
        "to": "id",
        "on_delete": "CASCADE"
      }
    ]
  },
  {
    "name": "publication_checks",
    "columns": [
      {
        "name": "observation_id",
        "type": "text",
        "notnull": 1,
        "pk": 1
      },
      {
        "name": "content_hash",
        "type": "text",
        "notnull": 1,
        "pk": 0
      },
      {
        "name": "decision",
        "type": "text",
        "notnull": 1,
        "pk": 0
      },
      {
        "name": "checked_at",
        "type": "text",
        "notnull": 1,
        "pk": 0
      }
    ],
    "foreignKeys": [
      {
        "from": "observation_id",
        "table": "observations",
        "to": "id",
        "on_delete": "CASCADE"
      }
    ]
  },
  {
    "name": "subscriptions",
    "columns": [
      {
        "name": "token_hash",
        "type": "text",
        "notnull": 1,
        "pk": 1
      },
      {
        "name": "user_id",
        "type": "text",
        "notnull": 1,
        "pk": 0
      },
      {
        "name": "token_envelope",
        "type": "text",
        "notnull": 1,
        "pk": 0
      },
      {
        "name": "product_id",
        "type": "text",
        "notnull": 1,
        "pk": 0
      },
      {
        "name": "plan",
        "type": "text",
        "notnull": 1,
        "pk": 0
      },
      {
        "name": "status",
        "type": "text",
        "notnull": 1,
        "pk": 0
      },
      {
        "name": "starts_at",
        "type": "text",
        "notnull": 1,
        "pk": 0
      },
      {
        "name": "expires_at",
        "type": "text",
        "notnull": 1,
        "pk": 0
      },
      {
        "name": "checked_at",
        "type": "integer",
        "notnull": 1,
        "pk": 0
      },
      {
        "name": "acknowledged",
        "type": "integer",
        "notnull": 1,
        "pk": 0
      },
      {
        "name": "is_trial",
        "type": "integer",
        "notnull": 1,
        "pk": 0
      },
      {
        "name": "paid_period_start",
        "type": "text",
        "notnull": 0,
        "pk": 0
      },
      {
        "name": "paid_period_end",
        "type": "text",
        "notnull": 0,
        "pk": 0
      },
      {
        "name": "latest_order_id",
        "type": "text",
        "notnull": 0,
        "pk": 0
      }
    ],
    "foreignKeys": [
      {
        "from": "user_id",
        "table": "users",
        "to": "id",
        "on_delete": "CASCADE"
      }
    ]
  },
  {
    "name": "service_config",
    "columns": [
      {
        "name": "key",
        "type": "text",
        "notnull": 1,
        "pk": 1
      },
      {
        "name": "envelope",
        "type": "text",
        "notnull": 1,
        "pk": 0
      },
      {
        "name": "updated_at",
        "type": "text",
        "notnull": 1,
        "pk": 0
      }
    ],
    "foreignKeys": []
  },
  {
    "name": "ai_reservations",
    "columns": [
      {
        "name": "id",
        "type": "text",
        "notnull": 1,
        "pk": 1
      },
      {
        "name": "day_key",
        "type": "text",
        "notnull": 1,
        "pk": 0
      },
      {
        "name": "reserved_micro_usd",
        "type": "integer",
        "notnull": 1,
        "pk": 0
      },
      {
        "name": "settled_micro_usd",
        "type": "integer",
        "notnull": 0,
        "pk": 0
      },
      {
        "name": "created_at",
        "type": "text",
        "notnull": 1,
        "pk": 0
      },
      {
        "name": "settled_at",
        "type": "text",
        "notnull": 0,
        "pk": 0
      }
    ],
    "foreignKeys": []
  },
  {
    "name": "identification_stages",
    "columns": [
      {
        "name": "observation_id",
        "type": "text",
        "notnull": 1,
        "pk": 1
      },
      {
        "name": "user_id",
        "type": "text",
        "notnull": 1,
        "pk": 0
      },
      {
        "name": "stage",
        "type": "text",
        "notnull": 1,
        "pk": 2
      },
      {
        "name": "data",
        "type": "text",
        "notnull": 1,
        "pk": 0
      },
      {
        "name": "created_at",
        "type": "text",
        "notnull": 1,
        "pk": 0
      }
    ],
    "foreignKeys": [
      {
        "from": "user_id",
        "table": "users",
        "to": "id",
        "on_delete": "CASCADE"
      },
      {
        "from": "observation_id",
        "table": "observations",
        "to": "id",
        "on_delete": "CASCADE"
      }
    ]
  }
] as const;
const INDEXES=[
  {
    "name": "ai_usage_user_period",
    "table": "ai_usage",
    "columns": [
      "user_id",
      "period"
    ]
  },
  {
    "name": "photo_allowance_owner_period",
    "table": "photo_allowance",
    "columns": [
      "user_id",
      "period"
    ]
  },
  {
    "name": "subscriptions_user_expiry",
    "table": "subscriptions",
    "columns": [
      "user_id",
      "expires_at"
    ]
  },
  {
    "name": "identification_stages_owner",
    "table": "identification_stages",
    "columns": [
      "user_id"
    ]
  }
] as const;
type Column={name:string;type:string;notnull:number;pk:number};
type ForeignKey={from:string;table:string;to:string;on_delete:string};
const paidColumns=['paid_period_start','paid_period_end','latest_order_id'] as const;
const ledgerSql='CREATE TABLE IF NOT EXISTS _trail_runtime_schema(version TEXT PRIMARY KEY NOT NULL,applied_at TEXT NOT NULL)';
async function columnInfo(db:D1Database){
  const result=await db.prepare('PRAGMA table_info("subscriptions")').all<Column>();
  if(!result.success)throw new RuntimeSchemaError();
  return result.results;
}
function validPaidColumn(columns:Column[],name:string){return columns.some(column=>column.name===name&&column.type.toLowerCase()==='text'&&Number(column.notnull)===0);}
async function initialise(db:D1Database){
  // A missing legacy parent schema is an incompatible environment, not licence
  // to manufacture/drop/replace someone else's membership or journal tables.
  const prerequisites=await db.batch<Column>([
    db.prepare('PRAGMA table_info("users")'),db.prepare('PRAGMA table_info("observations")'),db.prepare('PRAGMA table_info("auth_attempts")'),
  ]);
  for(const [index,names] of [[0,['id','email','name','password_hash','salt','created_at']],[1,['id','user_id','data','photo_key','updated_at']],[2,['key','count','reset_at']]] as const){
    if(!prerequisites[index]?.success||!names.every(name=>prerequisites[index]?.results.some(column=>column.name===name)))throw new RuntimeSchemaError();
  }
  const created=await db.batch(CREATE_STATEMENTS.map(sql=>db.prepare(sql)));
  if(created.some(result=>!result.success))throw new RuntimeSchemaError();
  for(const name of paidColumns){
    let columns=await columnInfo(db);
    if(!columns.some(column=>column.name===name)){
      try{
        // Names are a fixed compile-time allowlist, never request-derived.
        const result=await db.prepare('ALTER TABLE subscriptions ADD COLUMN '+name+' TEXT').run();
        if(!result.success)throw new RuntimeSchemaError();
      }catch{
        // Another isolate can win between PRAGMA and ALTER. Suppress an error
        // only when the exact additive postcondition now holds.
        columns=await columnInfo(db);
        if(!validPaidColumn(columns,name))throw new RuntimeSchemaError();
      }
    }
    if(!validPaidColumn(await columnInfo(db),name))throw new RuntimeSchemaError();
  }
  const checks=await db.batch<any>([
    ...TABLES.flatMap(table=>[db.prepare('PRAGMA table_info("'+table.name+'")'),db.prepare('PRAGMA foreign_key_list("'+table.name+'")')]),
    ...INDEXES.flatMap(index=>[db.prepare('PRAGMA index_list("'+index.table+'")'),db.prepare('PRAGMA index_info("'+index.name+'")')]),
  ]);
  if(checks.some(result=>!result.success))throw new RuntimeSchemaError();
  for(let i=0;i<TABLES.length;i++){
    const spec=TABLES[i],columns=checks[i*2]?.results as Column[]|undefined,foreignKeys=checks[i*2+1]?.results as ForeignKey[]|undefined;
    if(!columns||!foreignKeys||columns.length!==spec.columns.length||!spec.columns.every(expected=>columns.some(actual=>actual.name===expected.name&&actual.type.toLowerCase()===expected.type&&Number(actual.notnull)===expected.notnull&&Number(actual.pk)===expected.pk)))throw new RuntimeSchemaError();
    if(foreignKeys.length!==spec.foreignKeys.length||!spec.foreignKeys.every(expected=>foreignKeys.some(actual=>actual.from===expected.from&&actual.table===expected.table&&actual.to===expected.to&&actual.on_delete.toUpperCase()===expected.on_delete)))throw new RuntimeSchemaError();
  }
  for(let i=0;i<INDEXES.length;i++){
    const spec=INDEXES[i],offset=TABLES.length*2+i*2,listed=checks[offset]?.results,indexed=checks[offset+1]?.results;
    if(!listed?.some((index:any)=>index.name===spec.name)||!indexed||indexed.length!==spec.columns.length||!spec.columns.every((name,position)=>indexed[position]?.name===name))throw new RuntimeSchemaError();
  }
  const ledger=await db.prepare(ledgerSql).run();if(!ledger.success)throw new RuntimeSchemaError();
  const ledgerColumns=await db.prepare('PRAGMA table_info("_trail_runtime_schema")').all<Column>();
  if(!ledgerColumns.results.some(column=>column.name==='version'&&column.type.toLowerCase()==='text'&&column.pk===1)||!ledgerColumns.results.some(column=>column.name==='applied_at'&&column.type.toLowerCase()==='text'&&column.notnull===1))throw new RuntimeSchemaError();
  const marked=await db.prepare('INSERT INTO _trail_runtime_schema(version,applied_at) VALUES(?,?) ON CONFLICT(version) DO NOTHING').bind(RUNTIME_SCHEMA_VERSION,new Date().toISOString()).run();
  if(!marked.success)throw new RuntimeSchemaError();
}
/** A factory lets tests model independent isolates sharing the same SQLite DB. */
export function createRuntimeSchemaInitializer(){
  const pending=new WeakMap<D1Database,Promise<void>>();
  return function ensure(db:D1Database):Promise<void>{
    const existing=pending.get(db);if(existing)return existing;
    let task:Promise<void>;
    task=initialise(db).catch(()=>{if(pending.get(db)===task)pending.delete(db);throw new RuntimeSchemaError();});
    pending.set(db,task);return task;
  };
}
export const ensureRuntimeSchema=createRuntimeSchemaInitializer();
