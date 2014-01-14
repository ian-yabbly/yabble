## First Time

0. Pull down the code

    `git clone git@github.com:yabbly/yabble.git`

1. Set `APP_HOME`

    `export APP_HOME=$HOME/opt/yabble`

2. Run bootstrap

    `.../dev-bin/bootstrap`

3. Set up the DB

    First create a new user:

    ```
    $ psql
    postgres=# CREATE USER yabble WITH ENCRYPTED PASSWORD 'yabble';
    postgres=# \q
    ```

    WARNING: It is highly recommended that you run with user name `yabble` and
password `yabble`. You can run with a user not named `yabble` and a password
not `yabble`, but you will have to configure your environment correctly.
