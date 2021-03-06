(ns sse-chat.service
    (:require [io.pedestal.service.http :as bootstrap]
              [io.pedestal.service.http.route :as route]
              [io.pedestal.service.http.body-params :as body-params]
              [io.pedestal.service.http.route.definition :refer [defroutes]]
              [io.pedestal.service.http.sse :as sse]
              [io.pedestal.service.log :as log]
              [sse-chat.render :refer [erb]]))

(defn chat-page
  [request]
  (if-let [user (-> request :query-params :user)]
    (erb :chat {:user user})
    (erb :login)))

(def subscribers (atom []))

(defn add-subscriber [context]
  (swap! subscribers conj context))

(defn remove-subscriber [context]
  (log/info :msg "Removing user")
  (swap! subscribers #(remove #{context} %))
  (sse/end-event-stream context))

(defn publish
  [request]
  (doseq [sse-context @subscribers]
    (try
      (sse/send-event sse-context "message" (-> request :form-params (get "msg")))
      (catch java.io.IOException e
        (remove-subscriber sse-context))))
  {:status 204})

(defroutes routes
  [[["/" {:get chat-page :post publish}
     ^:interceptors [(body-params/body-params) bootstrap/html-body]
     ["/stream" {:get [::stream (sse/start-event-stream add-subscriber)]}]]]])

;; You can use this fn or a per-request fn via io.pedestal.service.http.route/url-for
(def url-for (route/url-for-routes routes))

;; Consumed by sse-chat.server/create-server
(def service {:env :prod
              ;; You can bring your own non-default interceptors. Make
              ;; sure you include routing and set it up right for
              ;; dev-mode. If you do, many other keys for configuring
              ;; default interceptors will be ignored.
              ;; :bootstrap/interceptors []
              ::bootstrap/routes routes

              ;; Uncomment next line to enable CORS support, add
              ;; string(s) specifying scheme, host and port for
              ;; allowed source(s):
              ;;
              ;; "http://localhost:8080"
              ;;
              ;;::boostrap/allowed-origins ["scheme://host:port"]

              ;; Root for resource interceptor that is available by default.
              ::bootstrap/resource-path "/public"

              ;; Either :jetty or :tomcat (see comments in project.clj
              ;; to enable Tomcat)
              ;;::bootstrap/host "localhost"
              ::bootstrap/type :jetty
              ::bootstrap/port (Integer. (or (System/getenv "PORT") 8080))})
