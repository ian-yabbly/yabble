/**
 * user
 **/
(function(window, document, require, define, undefined) {
    'use strict';

    define(
      [
        'strings'
      ],
      function(strings) {
        var User, loggedInUser;
        
        User = function(props) {
          props = props || {};
          this.id           = props.id;
          this.email        = props.email;
          this.displayName  = props.displayName;
        }
        
        User.prototype.getDisplayName = function() {
          var displayName, u;
          if((u = User.getLoggedInUser()) && u.id === this.id) {
            displayName = strings.get('user.me');
          } else if(this.email) {
            displayName = this.email;
          } else {
            displayName = this.displayName;
          }
          return displayName;
        };
        
        User.getLoggedInUser = function() {
          if(!loggedInUser && document.YABBLE_USER) {
            loggedInUser = new User(document.YABBLE_USER);
          }
          return loggedInUser;
        };
        
        User.setLoggedInUserEmail = function(email) {
          var user = User.getLoggedInUser();
          document.YABBLE_USER.email = user.email = email;
          return user;
        };
        
        return User;        
      }
    );

})(window, document, require, define);
