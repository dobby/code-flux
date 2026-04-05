import { createRouter, createWebHistory } from 'vue-router'
import HomeRedirectView from '../views/HomeRedirectView.vue'
import WorkspacePageView from '../views/WorkspacePageView.vue'
import WidgetCatalogView from '../views/WidgetCatalogView.vue'
import WidgetEditorView from '../views/WidgetEditorView.vue'
import JiraSettingsView from '../views/JiraSettingsView.vue'
import GeneralSettingsView from '../views/GeneralSettingsView.vue'
import SyncView from '../views/SyncView.vue'
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
      path: '/sync',
      name: 'sync',
      component: SyncView,
    },
    {
      path: '/explorer',
      name: 'explorer',
      component: ExplorerView,
    },
    {
      path: '/explorer/commit/:repoId/:commitSha',
      name: 'explorer-commit',
      component: ExplorerCommitDetailView,
      props: true,
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
