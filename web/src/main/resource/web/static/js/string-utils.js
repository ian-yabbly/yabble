/**
 * String utilities.
 */
(function(window, document, require, define, undefined) {
  'use strict';

  var reNonLetter = /[^A-Z]/i,
      reReplace = /\{\}/g;

  define({
    ellipsize: function(str, chars) {
      var i = chars - 1;
      if(typeof str === 'string' && str.length > chars) {
        while(reNonLetter.test(str[i]) && i > 0) {
          i--;
        }
        str = str.substr(0, i - 2) + '...';
      }
      return str;
    },
    format: function(str, r1, r2) {
      var replacements = Array.prototype.slice.call(arguments, 1),
          i = 0;
      return str.replace(reReplace, function(match) {
        return (replacements && replacements[i++]) || match;
      });
    }
  });

})(window, document, require, define);