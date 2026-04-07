import { createRouter, createWebHistory } from 'vue-router'
import HomeRedirectView from '../views/HomeRedirectView.vue'
import WorkspacePageView from '../views/WorkspacePageView.vue'
import WidgetCatalogView from '../views/WidgetCatalogView.vue'
import WidgetEditorView from '../views/WidgetEditorView.vue'
import JiraSettingsView from '../views/JiraSettingsView.vue'
import GeneralSettingsView from '../views/GeneralSettingsView.vue'
import SyncView from '../views/SyncView.vue'
import CodebaseView from '../views/CodebaseView.vue'
import ContributorsView from '../views/ContributorsView.vue'
import DashboardView from '../views/DashboardView.vue'
import ExplorerView from '../views/ExplorerView.vue'
import ExplorerCommitDetailView from '../views/ExplorerCommitDetailView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeRedirectView,
    },
    {
      path: '/settings/sync',
      alias: ['/sync'],
      name: 'settings-sync',
      component: SyncView,
    },
    {
      path: '/activity',
      alias: ['/explorer'],
      name: 'activity',
      component: ExplorerView,
    },
    {
      path: '/activity/commit/:repoId/:commitSha',
      alias: ['/explorer/commit/:repoId/:commitSha'],
      name: 'activity-commit',
      component: ExplorerCommitDetailView,
      props: true,
    },
    {
      path: '/codebase',
      name: 'codebase',
      component: CodebaseView,
    },
    {
      path: '/codebase/:repoId',
      name: 'codebase-repo',
      component: CodebaseView,
      props: true,
    },
    {
      path: '/contributors',
      name: 'contributors',
      component: ContributorsView,
    },
    {
      path: '/pages/:pageId',
      name: 'page',
      component: WorkspacePageView,
      props: true,
    },
    {
      path: '/widgets',
      name: 'widgets',
      component: WidgetCatalogView,
    },
    {
      path: '/widgets/new',
      name: 'widget-new',
      component: WidgetEditorView,
    },
    {
      path: '/widgets/:widgetId/edit',
      name: 'widget-edit',
      component: WidgetEditorView,
      props: true,
    },
    {
      path: '/settings/general',
      name: 'settings-general',
      component: GeneralSettingsView,
    },
    {
      path: '/settings/jira',
      name: 'settings-jira',
      component: JiraSettingsView,
    },
    {
      path: '/legacy/overview',
      name: 'legacy-overview',
      component: DashboardView,
    },
  ],
})
