'use strict';

angular.module('bluelatex', [
  'localization',
  'ngRoute',
  'bluelatex.Shared.Services.Configuration',
  'bluelatex.Shared.Services.WindowActive',
  'bluelatex.Paper.Controllers.EditPaper',
  'bluelatex.Paper.Controllers.NewPaper',
  'bluelatex.Paper.Controllers.Paper',
  'bluelatex.Paper.Controllers.Papers',
  'bluelatex.Shared.Controllers.Main',
  'bluelatex.Shared.Controllers.Menu',
  'bluelatex.Shared.Directives.Menu',
  'bluelatex.Shared.Directives.Messages',
  'bluelatex.Shared.Controllers.Messages',
  'bluelatex.User.Controllers.Login',
  'bluelatex.User.Controllers.Logout',
  'bluelatex.User.Controllers.Profile',
  'bluelatex.User.Controllers.Register',
  'bluelatex.User.Controllers.ResetPassword',
  'bluelatex.Latex.Directives.Vignette',
  'bluelatex.Latex.Services.SyncTexParser'
]).config(['$routeProvider',
  function ($routeProvider) {
    $routeProvider.when('/login', {
      templateUrl: 'partials/login.html',
      controller: 'LoginController',
      options: {
        name: 'login',
        connected: false,
        unconnected: true,
        title: 'Login'
      }
    });
    $routeProvider.when('/logout', {
      templateUrl: 'partials/logout.html',
      controller: 'LogoutController',
      options: {
        name: 'logout',
        connected: true,
        unconnected: false,
        title: 'Logout'
      }
    });
    $routeProvider.when('/register', {
      templateUrl: 'partials/register.html',
      controller: 'RegisterController',
      options: {
        name: 'register',
        connected: false,
        unconnected: true,
        title: 'Register'
      }
    });
    $routeProvider.when('/reset/?', {
      templateUrl: 'partials/reset.html',
      controller: 'ResetPasswordController',
      options: {
        name: 'reset',
        connected: false,
        unconnected: true,
        title: 'Login'
      }
    });
    $routeProvider.when('/:username/reset/:token/?', {
      templateUrl: 'partials/resetPassword.html',
      controller: 'ResetPasswordController',
      options: {
        name: 'resetPassword',
        connected: false,
        unconnected: true,
        title: 'Rset password'
      }
    });
    $routeProvider.when('/profile', {
      templateUrl: 'partials/profile.html',
      controller: 'ProfileController',
      options: {
        name: 'profile',
        connected: true,
        unconnected: false,
        title: 'Profile'
      }
    });
    $routeProvider.when('/papers', {
      templateUrl: 'partials/papers.html',
      controller: 'PapersController',
      options: {
        name: 'papers',
        connected: true,
        unconnected: false,
        title: 'Papers'
      }
    });
    $routeProvider.when('/paper/new', {
      templateUrl: 'partials/new_paper.html',
      controller: 'NewPaperController',
      options: {
        name: 'new_paper',
        connected: true,
        unconnected: false,
        title: 'New paper'
      }
    });
    $routeProvider.when('/paper/:id/edit', {
      templateUrl: 'partials/edit_paper.html',
      controller: 'EditPaperController',
      options: {
        name: 'edit_paper',
        connected: true,
        unconnected: false,
        title: 'Edit paper'
      }
    });
    $routeProvider.when('/paper/:id/?', {
      templateUrl: 'partials/paper.html',
      controller: 'PaperController',
      options: {
        name: 'paper',
        connected: true,
        unconnected: false,
        title: 'Paper'
      }
    });
    $routeProvider.when('/404/?', {
      templateUrl: 'partials/404.html',
      options: {
        name: 'new_paper',
        connected: true,
        unconnected: true,
        title: '404'
      }
    });
    $routeProvider.when('/', {
      redirectTo: '/papers',
      options: {}
    });

    $routeProvider.otherwise({
      redirectTo: '/404',
      options: {}
    });
  }
]).run(['$rootScope', '$location', '$route', '$window','$log','WindowActiveService',
  function ($rootScope, $location, $route, $window,$log,WindowActiveService) {
    $rootScope.loggedUser = {};
    var prev_page = null;
    $rootScope.$watch('loggedUser', function (value) {
      if ($rootScope.loggedUser.name == null && $route.current != null && !$route.current.$$route.options.unconnected) {
        // no logged user, we should be going to #login
        if ($route.current.$$route.options.name == "login") {
          // already going to #login, no redirect needed
        } else {
          prev_page = $location.path();
          // not going to #login, we should redirect now
          $location.path("/login");
        }
      } else if ($route.current != null && $route.current.$$route.options.connected == false && $rootScope.loggedUser.name != null) {
        if (prev_page != null && prev_page != '/login') {
          $location.path(prev_page);
        } else {
          $location.path("/");
        }
      }
    });
    // register listener to watch route changes
    $rootScope.$on("$routeChangeStart", function (event, next, current) {
      if ($rootScope.loggedUser.name == null && next.$$route != null && !next.$$route.options.unconnected) {
        // no logged user, we should be going to #login
        if (next.$$route.options.name == "login") {
          // already going to #login, no redirect needed
        } else {
          prev_page = $location.path();
          // not going to #login, we should redirect now
          $location.path("/login");
        }
      } else if (next.$$route != null && next.$$route.options.connected == false && $rootScope.loggedUser.name != null) {
        if (prev_page != null && prev_page != '/login') {
          $location.path(prev_page);
        } else {
          $location.path("/");
        }
      }
    });
  }
]);