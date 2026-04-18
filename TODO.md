# TODO

## Workspace Context Integration

The following modules need to be integrated into the workspace permission system
(refer to `UserController` as the reference implementation):

- [ ] **config** - ConfigController
- [ ] **param** - parameter management
- [ ] **worker** - WorkerController
- [ ] **catalog** - CatalogInfo endpoints
- [ ] **datasource** - datasource management
- [ ] **resource** - Resource endpoints
- [ ] **alert** - alert configuration

### Key Tasks

1. Add `@RequirePermission` to endpoints with appropriate permission levels based on operation type
2. Add `workspaceId` filter to data queries so users only see resources in their workspace
3. Validate that `RequestContext.getWorkspaceId()` matches the resource's workspace on write operations
