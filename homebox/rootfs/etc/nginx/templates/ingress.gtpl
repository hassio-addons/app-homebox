server {
    listen {{ .interface }}:{{ .port }} default_server;

    include /etc/nginx/includes/server_params.conf;
    include /etc/nginx/includes/proxy_params.conf;

    # Homebox answers every request with "X-Frame-Options: DENY", which no
    # iframe survives, and an Ingress panel is an iframe. It is replaced with
    # SAMEORIGIN, which is exactly as far as it needs to go: Ingress serves
    # this app from below the root of the Home Assistant origin. Direct access
    # is not framed by anything and keeps upstream's header as it is.
    proxy_hide_header X-Frame-Options;
    add_header X-Frame-Options SAMEORIGIN always;

    # Signing in through an identity provider ends with Homebox sending the
    # browser to "/home", which it builds from the site root rather than from
    # the request. Only redirects pointing back at this app are rewritten,
    # which leaves the one to the identity provider itself alone.
    absolute_redirect off;
    proxy_redirect http://$http_host/ $http_x_ingress_path/;
    proxy_redirect https://$http_host/ $http_x_ingress_path/;
    proxy_redirect / $http_x_ingress_path/;

    # Homebox builds its URLs from the site root, which is the wrong place when
    # Home Assistant hands it out from an Ingress path instead. That path is
    # only known per request, so it is written into the page on the way past.
    #
    # This is a handful of values in the small document Nuxt bootstraps from,
    # not a rewrite of the application: "baseURL" is what every route and
    # every runtime-built address is resolved against, and the patched-in
    # plugin reads SUB_PATH and points the whole API at it. The megabytes of
    # JavaScript behind them are never touched.
    #
    # The asset rewrites are pinned to the HTML attributes on purpose. The same
    # document carries "buildAssetsDir" in its inline config, and Nuxt joins
    # that onto "baseURL" at runtime when it fetches its app manifest. Rewrite
    # both and the manifest is requested with the path on it twice, Ingress
    # strips one copy, Homebox answers the leftover with the SPA fallback, and
    # Nuxt spends the rest of the session trying to read route rules out of an
    # HTML page.
    sub_filter_once off;
    sub_filter 'href="/_nuxt/' 'href="$http_x_ingress_path/_nuxt/';
    sub_filter 'src="/_nuxt/' 'src="$http_x_ingress_path/_nuxt/';
    sub_filter 'src="/set-theme.js"' 'src="$http_x_ingress_path/set-theme.js"';
    sub_filter 'SUB_PATH:""' 'SUB_PATH:"$http_x_ingress_path"';
    sub_filter 'app:{baseURL:"/"' 'app:{baseURL:"$http_x_ingress_path/"';

    location / {
        allow   172.30.32.2;
        deny    all;

        proxy_pass http://backend;
    }
}
