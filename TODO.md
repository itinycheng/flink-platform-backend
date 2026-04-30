# TODO

## Add `@RequirePermission` to Controllers

Workspace integration is complete. The remaining task is to add `@RequirePermission`
annotations to the endpoints below, following the same pattern as `JobFlowController`.

- [ ] **AlertController** - create, update, delete
- [ ] **CatalogInfoController** - create, update, delete
- [ ] **DatasourceController** - create, update, delete
- [ ] **ResourceController** - create, update, delete
- [ ] **JobParamController** - create, update, delete
- [ ] **TagInfoController** - create, update, delete

---

## WorkspaceScopedService — Automatic Workspace Isolation for Save/Update/Delete

### Problem

Controllers correctly inject `workspaceId` on `create`, but `getById`, `updateById`, and
`removeById` have no workspace ownership check. Any authenticated user who knows a resource ID
can read, modify, or delete resources belonging to another workspace.

### Solution

Introduce a `WorkspaceScopedService` abstract base class that overrides the standard
MyBatis-Plus methods to enforce workspace ownership transparently. Controllers require no
changes beyond removing now-redundant `setWorkspaceId(...)` calls.

### Architecture

```
dao layer (no dependency on web layer)
  WorkspaceOwned           entity interface — exposes getWorkspaceId() / setWorkspaceId()
  WorkspaceIdProvider      decoupling interface — defined in dao, implemented in web
  WorkspaceScopedService   abstract base — overrides save / getById / updateById / removeById

web layer
  RequestContextWorkspaceIdProvider   reads ThreadLocal, implements WorkspaceIdProvider
```

**Fallback rule:** when `workspaceId == null` (non-HTTP context: Quartz, schedulers, etc.),
all overridden methods delegate directly to the super implementation — no side effects.

### Behavior of Overridden Methods

| Method               | HTTP context (wsId present)                              | Non-HTTP context (wsId == null) |
|----------------------|----------------------------------------------------------|---------------------------------|
| `save(entity)`       | auto-sets `workspaceId` if not already set               | delegates to super              |
| `getById(id)`        | returns `null` if resource belongs to another workspace  | delegates to super              |
| `updateById(entity)` | returns `false` if resource belongs to another workspace | delegates to super              |
| `removeById(id)`     | returns `false` if resource belongs to another workspace | delegates to super              |

### Files to Create

- [ ] `flink-platform-dao/.../entity/WorkspaceOwned.java`
- [ ] `flink-platform-dao/.../service/WorkspaceIdProvider.java`
- [ ] `flink-platform-dao/.../service/WorkspaceScopedService.java`
- [ ] `flink-platform-web/.../config/RequestContextWorkspaceIdProvider.java`

### Entities — add `implements WorkspaceOwned` (class declaration only, Lombok handles the rest)

- [ ] `AlertInfo` / `CatalogInfo` / `Datasource` / `TagInfo` / `Resource` / `JobParam` / `JobFlow`

### Services — change `extends ServiceImpl` → `extends WorkspaceScopedService`

| Service              | Entity        |
|----------------------|---------------|
| `AlertService`       | `AlertInfo`   |
| `CatalogInfoService` | `CatalogInfo` |
| `DatasourceService`  | `Datasource`  |
| `TagInfoService`     | `TagInfo`     |
| `ResourceService`    | `Resource`    |
| `JobParamService`    | `JobParam`    |
| `JobFlowService`     | `JobFlow`     |

### Controller Cleanup

- [ ] Remove explicit `setWorkspaceId(RequestContext.requireWorkspaceId())` calls from all `create`
  methods — `save()` now injects it automatically
- [ ] `CatalogInfoController.delete`: replace `remove(QueryWrapper with workspaceId condition)` with
  `removeById(catalogId)` — ownership is enforced by the base class

### Out of Scope

- `JobFlowRun` — `workspaceId` is set internally from `JobFlow` by the scheduler, not via HTTP
- `JobInfo` — has no `workspaceId` field
- `list`/`page` queries with manual `eq(workspaceId)` conditions — remain unchanged

