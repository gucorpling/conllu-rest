(ns conllu-rest.server.xtdb
  (:require [xtdb.api :as xt]
            [mount.core :refer [defstate]]
            [conllu-rest.xtdb.easy :refer [install-tx-fns!]]
            [conllu-rest.server.config :refer [env]]
            [conllu-rest.server.nlp :as nlp]
            [conllu-rest.server.nlp.listen :as nlpl]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [conllu-rest.xtdb.queries :as queries])
  (:import [xtdb.api IXtdb]))

;; Proper XTDB stuff --------------------------------------------------------------------------------
(defn ^IXtdb start-standalone-xtdb-node [{:keys [db-dir http-server-port]}]
  (let [dirf #(str db-dir "/" %)]
    (xt/start-node
      (-> {:xtdb/tx-log         {:kv-store {:xtdb/module `xtdb.rocksdb/->kv-store, :db-dir (dirf "tx-log")}}
           :xtdb/document-store {:kv-store {:xtdb/module `xtdb.rocksdb/->kv-store, :db-dir (dirf "docs")}}
           :xtdb/index-store    {:kv-store {:xtdb/module `xtdb.rocksdb/->kv-store, :db-dir (dirf "indexes")}}}
          (cond-> http-server-port (assoc :xtdb.http-server/server {:port http-server-port}))))))

(defn start-main-node []
  (start-standalone-xtdb-node {:db-dir           (-> env ::config :main-db-dir)
                               :http-server-port (-> env ::config :http-server-port)}))


(defstate xtdb-node
  :start (let [node (start-main-node)
               f (partial nlpl/xtdb-listen node nlp/agent-map)]
           (install-tx-fns! node)
           (xt/listen node {::xt/event-type ::xt/indexed-tx, :with-tx-ops? true} f)
           (nlpl/assign-jobs node nlp/agent-map)
           node)
  :stop (.close xtdb-node))