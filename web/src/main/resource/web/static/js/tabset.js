(function(window, document, require, define, undefined) {
    'use strict';

    define(
        [
            'jquery',
            'tab'
        ],
        function($, Tab) {

            var TabSet,
                supportsHistoryAPI  = window && window.history && window.history.pushState,
                reTab               = /tab\/([^\/]*)/;

            TabSet = function(props) {
                var self = this;
                props = props || {};

                if(!props.tabs) {
                    throw 'You must specify a tabs selector';
                }

                this.activeTabId    = undefined;
                this.tabs           = {};

                $(props.tabs).each(function(i, el) {
                    var m, id, contentId;

                    el          = $(el);
                    m           = reTab.exec(el.attr('href'));
                    id          = (m && m.pop()) || "";
                    contentId   = el.data('tab-content-id');

                    self.tabs[id] = new Tab(id, el, $('#' + contentId));

                    if(self.tabs[id].isActive()) {
                        self.activeTabId = id;
                    }

                    if(supportsHistoryAPI) {
                        el.click(function() {
                            window.history.pushState(
                                { type : 'tab' , tabId : id },
                                document.location.title,
                                el.attr('href')
                            );
                            self.activate(id);
                            return false;
                        });
                    }
                });

                if(supportsHistoryAPI) {
                    $(window).bind('popstate', function(e) {
                        if(e.originalEvent && e.originalEvent.state && e.originalEvent.state.type === 'tab') {
                            self.activate(e.originalEvent.state.tabId);
                        }
                    });
                }
            };

            TabSet.prototype.activate = function(id) {
                var tab         = this.getTab(id),
                    activeTab   = this.getTab(this.activeTabId);

                if(activeTab) {
                    activeTab.deActivate()
                    this.activeTabId = undefined;
                }
                if(tab) {
                    tab.activate();
                    this.activeTabId = id;
                }
                return this;
            };

            TabSet.prototype.getTab = function(id) {
                return this.tabs[id];
            };

            return TabSet;
        }
    );

})(window, document, require, define);