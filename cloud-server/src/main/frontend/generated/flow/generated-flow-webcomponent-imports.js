import { injectGlobalWebcomponentCss } from 'Frontend/generated/jar-resources/theme-util.js';

import { injectGlobalCss } from 'Frontend/generated/jar-resources/theme-util.js';

import $cssFromFile_0 from '@vaadin/vaadin-lumo-styles/lumo.css?inline';

injectGlobalWebcomponentCss($cssFromFile_0.toString());
import '@vaadin/vertical-layout/src/vaadin-vertical-layout.js';
import '@vaadin/login/src/vaadin-login-form.js';
import '@vaadin/app-layout/src/vaadin-app-layout.js';
import '@vaadin/button/src/vaadin-button.js';
import '@vaadin/tooltip/src/vaadin-tooltip.js';
import 'Frontend/generated/jar-resources/disableOnClickFunctions.js';
import '@vaadin/horizontal-layout/src/vaadin-horizontal-layout.js';
import '@vaadin/app-layout/src/vaadin-drawer-toggle.js';
import '@vaadin/markdown/src/vaadin-markdown.js';
import '@vaadin/grid/src/vaadin-grid.js';
import '@vaadin/grid/src/vaadin-grid-column.js';
import '@vaadin/grid/src/vaadin-grid-sorter.js';
import '@vaadin/checkbox/src/vaadin-checkbox.js';
import 'Frontend/generated/jar-resources/flow-component-renderer.js';
import 'Frontend/generated/jar-resources/flow-component-directive.js';
import 'lit';
import 'Frontend/generated/jar-resources/gridConnector.ts';
import '@vaadin/component-base/src/debounce.js';
import '@vaadin/component-base/src/async.js';
import '@vaadin/grid/src/vaadin-grid-active-item-mixin.js';
import 'Frontend/generated/jar-resources/vaadin-grid-flow-selection-column.js';
import '@vaadin/grid/src/vaadin-grid-column-group.js';
import 'Frontend/generated/jar-resources/lit-renderer.ts';
import 'lit/directives/live.js';
import '@vaadin/context-menu/src/vaadin-context-menu.js';
import 'Frontend/generated/jar-resources/contextMenuConnector.js';
import 'Frontend/generated/jar-resources/contextMenuTargetConnector.js';
import '@vaadin/component-base/src/gestures.js';
import '@vaadin/text-field/src/vaadin-text-field.js';
import '@vaadin/confirm-dialog/src/vaadin-confirm-dialog.js';
import '@vaadin/notification/src/vaadin-notification.js';
import '@vaadin/tabs/src/vaadin-tabs.js';
import '@vaadin/tabs/src/vaadin-tab.js';
import '@vaadin/integer-field/src/vaadin-integer-field.js';
import '@vaadin/dialog/src/vaadin-dialog.js';
import '@vaadin/combo-box/src/vaadin-combo-box.js';
import 'Frontend/generated/jar-resources/comboBoxConnector.js';
import '@vaadin/combo-box/src/vaadin-combo-box-placeholder.js';
import '@vaadin/multi-select-combo-box/src/vaadin-multi-select-combo-box.js';
import '@vaadin/number-field/src/vaadin-number-field.js';
import '@vaadin/common-frontend/ConnectionIndicator.js';
import 'Frontend/generated/jar-resources/ReactRouterOutletElement.tsx';
import 'react-router';
import 'react';
const loadOnDemand = (key) => {
  const pending = [];
  if (key === 'ba2b90ef9691d7f73fded3f8a933b0937e84789fcd34120982d61b1423c82350') {
    pending.push(import('./chunks/chunk-5123e2cc937ac7fdea348083a482bab16b53954330c77da5f598fd06b60945f5.js'));
  }
  if (key === '5dc57ae244814f8fa6626682d051a25710656be7f0d37455273410051c429e84') {
    pending.push(import('./chunks/chunk-83cbd41b83358cc0b9688cd53e75642345e18a5a44af90f5bc75bf9e43723b56.js'));
  }
  if (key === '9b21fc8e2913d5ce4dcd85d58d09f343c6ef1f8b13e5d25586ecebe2b1bddcb9') {
    pending.push(import('./chunks/chunk-5123e2cc937ac7fdea348083a482bab16b53954330c77da5f598fd06b60945f5.js'));
  }
  if (key === 'e72e5530d3bfe285f30731c2daa40426af27680283d8cb1c570750954836d54d') {
    pending.push(import('./chunks/chunk-13f437bb906121a6687763554f79322053f84425727a7cf575f6fb47099f113a.js'));
  }
  if (key === '9e5085341e0c1c7a1741e5f37da7a3ce1d7cc808e9ad89ed298e5ad243295978') {
    pending.push(import('./chunks/chunk-dfa3f0685f3fe14780cdb5192e1944cabdfe59fa48f0810012e906b8f24628a6.js'));
  }
  return Promise.all(pending);
}
window.Vaadin = window.Vaadin || {};
window.Vaadin.Flow = window.Vaadin.Flow || {};
window.Vaadin.Flow.loadOnDemand = loadOnDemand;
window.Vaadin.Flow.resetFocus = () => {
 let ae=document.activeElement;
 while(ae&&ae.shadowRoot) ae = ae.shadowRoot.activeElement;
 return !ae || ae.blur() || ae.focus() || true;
}