server {
    listen {{ .interface }}:{{ .port }} default_server;

    include /etc/nginx/includes/server_params.conf;
    include /etc/nginx/includes/proxy_params.conf;

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
    # This is four values in the small document Nuxt bootstraps from, not a
    # rewrite of the application: "baseURL" is what every lazily loaded chunk
    # and every route is resolved against, and the patched-in plugin reads
    # SUB_PATH and points the whole API at it. The megabytes of JavaScript
    # behind them are never touched.
    sub_filter_once off;
    sub_filter '"/_nuxt/' '"$http_x_ingress_path/_nuxt/';
    sub_filter 'src="/set-theme.js"' 'src="$http_x_ingress_path/set-theme.js"';
    sub_filter 'SUB_PATH:""' 'SUB_PATH:"$http_x_ingress_path"';
    sub_filter 'app:{baseURL:"/"' 'app:{baseURL:"$http_x_ingress_path/"';

    location / {
        allow   172.30.32.2;
        deny    all;

        proxy_pass http://backend;
    }
}
