(function(window, document, require, define, undefined) {
  'use strict';

  require(
      [
        'jquery'
      ],
      function($) {
        var activeMenuTrigger,
            $document = $(document),
            hideActiveMenu = function() {
              if(activeMenuTrigger) {
                activeMenuTrigger.removeClass('show-menu');
                activeMenuTrigger = undefined;
              }
              $document.unbind('click', onClickHideMenu);
              return false;
            },
            onClickHideMenu = function(e) {
              var target = $(e.target);
              if(!target.is('.show-menu') && target.parents('.show-menu:first').length === 0) {
                hideActiveMenu();
              }
            };

        $(function() {
          $('.menu-trigger').each(function(i, el) {
            $(el).bind('click', function(e) {
              var f, t,
                  origTarget = $(e.target),
                  isClickOnTrigger = origTarget.is('.menu-trigger');

              if(!isClickOnTrigger) {
                t = origTarget.parents('.menu-trigger:first');
              } else {
                t = origTarget;
              }
              if(
                  (isClickOnTrigger || origTarget.data('menu-close-trigger')) &&
                      activeMenuTrigger && activeMenuTrigger.get(0) === t.get(0)
                  ) {
                return hideActiveMenu();
              }
              if(t && t.length > 0 && (!activeMenuTrigger || activeMenuTrigger.get(0) !== t.get(0))) {
                if(activeMenuTrigger) {
                  hideActiveMenu();
                }
                t.addClass('show-menu');
                activeMenuTrigger = t;
                if((f = $(activeMenuTrigger.data('focus-on-click'))) && f.length === 1) {
                  f.focus();
                }
                $document.bind('click', onClickHideMenu);
              }
            });
          });
        });
      }
  );

})(window, document, require, define);