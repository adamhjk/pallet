(ns pallet.core-test
  (:use pallet.core)
  (require
   [pallet.utils :as utils]
   [pallet.stevedore :as stevedore]
   [pallet.resource.exec-script :as exec-script]
   [pallet.compute :as compute]
   [pallet.compute.node-list :as node-list]
   [pallet.target :as target]
   [pallet.mock :as mock]
   [pallet.resource :as resource]
   [pallet.resource-build :as resource-build]
   [pallet.test-utils :as test-utils])
  (:use
   clojure.test))

;; tests run with node-list, as no external dependencies

;; Allow running against other compute services if required
(deftest with-admin-user-test
  (let [x (rand)]
    (with-admin-user [x]
      (is (= x (:username pallet.utils/*admin-user*))))))

;; this test doesn't work too well if the test are run in more than
;; one thread...
#_
(deftest admin-user-test
  (let [username "userfred"
        old pallet.utils/*admin-user*]
    (admin-user username)
    (is (map? pallet.utils/*admin-user*))
    (is (= username (:username pallet.utils/*admin-user*)))
    (is (= (pallet.utils/default-public-key-path)
           (:public-key-path pallet.utils/*admin-user*)))
    (is (= (pallet.utils/default-private-key-path)
           (:private-key-path pallet.utils/*admin-user*)))
    (is (nil? (:password pallet.utils/*admin-user*)))

    (admin-user username :password "pw" :public-key-path "pub"
                :private-key-path "pri")
    (is (map? pallet.utils/*admin-user*))
    (is (= username (:username pallet.utils/*admin-user*)))
    (is (= "pub" (:public-key-path pallet.utils/*admin-user*)))
    (is (= "pri" (:private-key-path pallet.utils/*admin-user*)))
    (is (= "pw" (:password pallet.utils/*admin-user*)))

    (admin-user old)
    (is (= old pallet.utils/*admin-user*))))


(deftest add-prefix-to-node-type-test
  (is (= {:tag :pa} (add-prefix-to-node-type "p" {:tag :a}))))

(deftest add-prefix-to-node-map-test
  (is (= {{:tag :pa} 1} (add-prefix-to-node-map "p" {{:tag :a} 1}))))

(deftest node-count-difference-test
  (is (= { {:tag :a} 1 {:tag :b} -1}
         (node-count-difference
          { {:tag :a} 2 {:tag :b} 0}
          [(test-utils/make-node "a") (test-utils/make-node "b")])))
  (is (= { {:tag :a} 1 {:tag :b} 1}
         (node-count-difference { {:tag :a} 1 {:tag :b} 1} []))))

(deftest add-os-family-test
  (defnode a {:os-family :ubuntu})
  (defnode b {})
  (let [n1 (test-utils/make-node "n1")]
    (is (= {:tag :a :image {:os-family :ubuntu} :phases nil}
           (:node-type
            (add-os-family
             {:target-node n1 :node-type a})))))
  (let [n1 (test-utils/make-node "n1")]
    (is (= {:tag :a :image {:os-family :ubuntu} :phases nil}
           (:node-type
            (add-os-family
             {:target-node n1 :node-type a}))))
    (is (= {:tag :b :image {:os-family :ubuntu} :phases nil}
           (:node-type
            (add-os-family
             {:target-node n1 :node-type b}))))))

(deftest add-target-packager-test
  (is (= {:node-type {:image {:os-family :ubuntu}} :target-packager :aptitude}
         (add-target-packager
          {:node-type {:image {:os-family :ubuntu}}}))))

(deftest converge-node-counts-test
  (defnode a {:os-family :ubuntu})
  (let [a-node (test-utils/make-node "a" :running true)
        compute (compute/compute-service "node-list" :node-list [a-node])]
    (converge-node-counts
     {a 1} [a-node] {:compute compute})))

(deftest nodes-in-map-test
  (defnode a {:os-family :ubuntu})
  (defnode b {:os-family :ubuntu})
  (let [a-node (test-utils/make-node "a")
        b-node (test-utils/make-node "b")
        nodes [a-node b-node]]
    (is (= [a-node]
           (nodes-in-map {a 1} nodes)))
    (is (= [a-node b-node]
           (nodes-in-map {a 1 b 2} nodes)))))

(deftest nodes-in-set-test
  (defnode a {:os-family :ubuntu})
  (defnode b {:os-family :ubuntu})
  (defnode pa {:os-family :ubuntu})
  (defnode pb {:os-family :ubuntu})
  (let [a-node (test-utils/make-node "a")
        b-node (test-utils/make-node "b")]
    (is (= {a #{a-node}}
           (nodes-in-set {a a-node} nil nil)))
    (is (= {a #{a-node b-node}}
           (nodes-in-set {a #{a-node b-node}} nil nil)))
    (is (= {a #{a-node} b #{b-node}}
           (nodes-in-set {a #{a-node} b #{b-node}} nil nil))))
  (let [a-node (test-utils/make-node "a")
        b-node (test-utils/make-node "b")]
    (is (= {pa #{a-node}}
           (nodes-in-set {a a-node} "p" nil)))
    (is (= {pa #{a-node b-node}}
           (nodes-in-set {a #{a-node b-node}} "p" nil)))
    (is (= {pa #{a-node} pb #{b-node}}
           (nodes-in-set {a #{a-node} b #{b-node}} "p" nil)))
    (is (= {pa #{a-node} pb #{b-node}}
           (nodes-in-set {a a-node b b-node} "p" nil)))))

(deftest node-in-types?-test
  (defnode a {})
  (defnode b {})
  (is (node-in-types? [a b] (test-utils/make-node "a")))
  (is (not (node-in-types? [a b] (test-utils/make-node "c")))))

(deftest nodes-for-type-test
  (defnode a {})
  (defnode b {})
  (let [na (test-utils/make-node "a")
        nb (test-utils/make-node "b")
        nc (test-utils/make-node "c")]
    (is (= [nb] (nodes-for-type [na nb nc] b)))
    (is (= [na] (nodes-for-type [na nc] a)))))

(deftest nodes-in-map-test
  (defnode a {})
  (defnode b {})
  (defnode c {})
  (let [na (test-utils/make-node "a")
        nb (test-utils/make-node "b")]
    (is (= [na nb] (nodes-in-map {a 1 b 1 c 1} [na nb])))
    (is (= [na] (nodes-in-map {a 1 c 1} [na nb])))))

(deftest build-request-map-test
  (binding [pallet.core/*middleware* :middleware]
    (testing "defaults"
      (is (= {:blobstore nil :compute nil :user utils/*admin-user*
              :middleware :middleware}
             (#'pallet.core/build-request-map {}))))
    (testing "passing a prefix"
      (is (= {:blobstore nil :compute nil :prefix "prefix"
              :user utils/*admin-user* :middleware *middleware*}
             (#'pallet.core/build-request-map {:prefix "prefix"}))))
    (testing "passing a user"
      (let [user (utils/make-user "fred")]
        (is (= {:blobstore nil :compute nil  :user user
                :middleware :middleware}
               (#'pallet.core/build-request-map {:user user})))))))

(resource/defresource test-component
  (test-component-fn
   [request arg & options]
   (str arg)))

(deftest make-node-test
  (is (= {:tag :fred :image {:os-family :ubuntu} :phases nil}
         (make-node "fred" {:os-family :ubuntu})))
  (is (= {:tag :tom :image {:os-family :centos} :phases nil}
         (make-node "tom" {:os-family :centos}))))

(deftest defnode-test
  (defnode fred {:os-family :ubuntu})
  (is (= {:tag :fred :image {:os-family :ubuntu} :phases nil} fred))
  (defnode tom "This is tom" {:os-family :centos})
  (is (= {:tag :tom :image {:os-family :centos} :phases nil} tom))
  (is (= "This is tom" (:doc (meta #'tom))))
  (defnode harry (tom :image))
  (is (= {:tag :harry :image {:os-family :centos} :phases nil} harry))
  (defnode with-phases (tom :image)
    :bootstrap (resource/phase (test-component :a))
    :configure (resource/phase (test-component :b)))
  (is (= #{:bootstrap :configure} (set (keys (with-phases :phases)))))
  (let [request {:target-node (test-utils/make-node "tag" :id "id")
                 :target-id :id
                 :node-type with-phases
                 :target-packager :yum}]
    (is (= ":a\n"
           (first
            (resource-build/produce-phases
             [:bootstrap]
             (resource-invocations (assoc request :phase :bootstrap))))))
    (is (= ":b\n"
           (first
            (resource-build/produce-phases
             [:configure]
             (resource-invocations (assoc request :phase :configure))))))))

(resource/defresource identity-resource
  (identity-resource* [request x] x))

(resource/deflocal identity-local-resource
  (identity-local-resource* [request] request))

(deftest produce-init-script-test
  (is (= "a\n"
         (produce-init-script
          {:node-type {:image {:os-family :ubuntu}
                       :phases {:bootstrap (resource/phase
                                            (identity-resource "a"))}}
           :target-id :id})))
  (testing "rejects local resources"
    (is (thrown?
         clojure.contrib.condition.Condition
         (produce-init-script
          {:node-type
           {:image {:os-family :ubuntu}
            :phases {:bootstrap (resource/phase (identity-local-resource))}}
           :target-id :id})))))



(defmacro seen-fn
  "Generate a local function, which uses an atom to record when it is called."
  [name]
  (let [localf-sym (gensym "localf")
        localf*-sym (gensym "localf*")
        seen-sym (gensym "seen")]
    `(let [~seen-sym (atom nil)
           seen?# (fn [] @~seen-sym)]
       (resource/deflocal ~localf-sym
         (~localf*-sym
          [request#]
          (clojure.contrib.logging/info (format "Seenfn %s" ~name))
          (is (not @~seen-sym))
          (reset! ~seen-sym true)
          (is (:target-node request#))
          (is (:node-type request#))
          request#))
       [~localf-sym seen?#])))

(deftest lift-test
  (defnode local {})
  (testing "node-list"
    (let [[localf seen?] (seen-fn "1")
          service (compute/compute-service
                   "node-list"
                   :node-list [(node-list/make-localhost-node
                                :tag "local" :os-family :ubuntu)])]
      (is (.contains
           "bin"
           (with-out-str
             (lift local
                   :phase [(resource/phase (exec-script/exec-script (ls "/")))
                           (resource/phase (localf))]
                   :user (assoc utils/*admin-user*
                           :username (test-utils/test-username)
                           :no-sudo true)
                   :compute service))))
      (is (seen?)))))

(deftest lift2-test
  (let [[localf seen?] (seen-fn "x")
        [localfy seeny?] (seen-fn "y")
        compute (compute/compute-service
                 "node-list"
                 :node-list [(node-list/make-localhost-node
                              :tag "x1" :name "x1" :id "x1"
                              :os-family :ubuntu)
                             (node-list/make-localhost-node
                              :tag "y1" :name "y1" :id "y1"
                              :os-family :ubuntu)])
        x1 (make-node "x1" {} :configure (resource/phase localf))
        y1 (make-node "y1" {} :configure (resource/phase localfy))]
    (is (map?
         (lift [x1 y1]
               :user (assoc utils/*admin-user*
                       :username (test-utils/test-username)
                       :no-sudo true)
               :compute compute)))
    (is (seen?))
    (is (seeny?))))

(deftest lift*-nodes-binding-test
  (defnode a {})
  (defnode b {})
  (let [na (test-utils/make-node "a")
        nb (test-utils/make-node "b")
        nc (test-utils/make-node "c" :running false)]
    (mock/expects [(sequential-apply-phase
                    [request nodes]
                    (do
                      (is (= #{na nb} (set (:all-nodes request))))
                      (is (= #{na nb} (set (:target-nodes request))))
                      []))]
                  (lift* {a #{na nb nc}} nil [:configure]
                         {:compute nil
                          :user utils/*admin-user*
                          :middleware *middleware*}))
    (mock/expects [(sequential-apply-phase
                    [request nodes]
                    (do
                      (is (= #{na nb} (set (:all-nodes request))))
                      (is (= #{na nb} (set (:target-nodes request))))
                      []))]
                  (lift* {a #{na} b #{nb}} nil [:configure]
                         {:compute nil
                          :user utils/*admin-user*
                          :middleware *middleware*}))))

(deftest lift-multiple-test
  (defnode a {})
  (defnode b {})
  (let [na (test-utils/make-node "a")
        nb (test-utils/make-node "b")
        nc (test-utils/make-node "c")
        compute (compute/compute-service "node-list" :node-list [na nb nc])]
    (mock/expects [(compute/nodes [_] [na nb nc])
                   (sequential-apply-phase
                    [request nodes]
                    (do
                      (is (= #{na nb nc} (set (:all-nodes request))))
                      (is (= #{na nb} (set (:target-nodes request))))
                      []))]
                  (lift [a b] :compute compute))))

;; (deftest converge*-nodes-binding-test
;;   (defnode a {})
;;   (defnode b {})
;;   (let [na (test-utils/make-node "a")
;;         nb (test-utils/make-node "b")
;;         nc (test-utils/make-node "b" :name "b1" :running false)
;;         compute (compute/compute-service "node-list" :node-list [na nb nc])]
;;     (mock/expects [(sequential-apply-phase
;;                     [request nodes]
;;                     (do
;;                       (is (= #{na nb} (set (:all-nodes request))))
;;                       (is (= #{na nb} (set (:target-nodes request))))))
;;                    (compute/nodes [& _] [na nb nc])]
;;                   (converge*
;;                    {a 1 b 1} nil [:configure]
;;                    {:compute compute
;;                     :middleware *middleware*}))))

;; (deftest converge-test
;;   (let [id "a"
;;         request (with-middleware
;;                   wrap-no-exec
;;                   (converge {(make-node
;;                               "a" {}
;;                               :configure (fn [request]
;;                                            (resource/invoke-resource
;;                                             request
;;                                             (fn [request] "Hi")
;;                                             [] :in-sequence :script/bash)))
;;                              1} :compute nil))]
;;     (is (map? request))
;;     (is (map? (-> request :results)))
;;     (is (map? (-> request :results first second)))
;;     (is (:configure (-> request :results first second)))
;;     (is (some
;;          #(= "Hi\n" %)
;;          (:configure (-> request :results first second))))
;;     (is (= 1 (count (:all-nodes request))))
;;     (is (= 1 (count (compute/nodes))))))
