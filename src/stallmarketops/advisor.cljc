(ns stallmarketops.advisor
  "StallMarketAdvisor -- the *contained intelligence node* for the
  ISIC-4789 'Retail sale via stalls and markets of other goods' (the
  catch-all market-stall vertical for general/other goods not covered by
  ISIC 4781's food/beverage/tobacco stalls or ISIC 4782's
  textiles/clothing/footwear stalls) operations-coordination actor.

  It drafts exactly four kinds of back-office proposal from a closed
  allowlist: sales/inventory transaction logging, stall
  placement/staffing scheduling, inventory supply-order coordination, and
  compliance-concern flagging. CRITICAL: it is a smart-but-untrusted
  advisor. It returns a *proposal* (with a rationale + the fields it
  cited), never a committed record and NEVER a direct actuation -- every
  proposal's `:effect` is always `:propose`. Every output is censored
  downstream by `stallmarketops.governor` before anything touches the
  SSoT.

  This advisor NEVER drafts a proposal that directly issues or finalizes
  a market-stall permit, and NEVER directly resolves a vendor/customer
  dispute -- those are permanently out of scope for this actor, not
  merely un-implemented. `stallmarketops.governor`'s
  `scope-exclusion-violations` independently re-scans every proposal for
  exactly this failure mode (a compromised or confused advisor drifting
  into scope it must never touch) and HARD-holds it, regardless of
  confidence or op.

  Like every sibling actor's advisor, this is a deterministic mock so the
  actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:op         kw             ; echoes the request op
     :stall-id   str
     :summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the scope-exclusion gate
     :cites      [str ..]       ; facts/sources the advisor used -- SCANNED too
     :effect     :propose       ; ALWAYS :propose -- never a direct actuation
     :value      map            ; the draft payload a human/system would review
     :confidence 0..1}")

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

;; ----------------------------- proposal generators -----------------------------

(defn- propose-sales-record
  "Draft a sales/inventory transaction log entry. Pure logging of
  observed transactions (units sold, stock-count deltas) -- never a
  unit-price decision."
  [_db {:keys [stall-id patch]}]
  {:op         :log-sales-record
   :stall-id   stall-id
   :summary    (str stall-id " の販売/在庫記録を記録: " (pr-str (keys patch)))
   :rationale  "販売数量・在庫カウントの観察記録のみ。値付けの判断は含まない。"
   :cites      [stall-id]
   :effect     :propose
   :value      (merge {:stall-id stall-id} patch)
   :confidence 0.92})

(defn- propose-stall-operation
  "Draft a stall placement/staffing scheduling proposal (a pitch
  assignment or roster entry, never a direct permit action)."
  [_db {:keys [stall-id patch]}]
  {:op         :schedule-stall-operation
   :stall-id   stall-id
   :summary    (str stall-id " の出店配置/要員配置予定を提案: " (pr-str (keys patch)))
   :rationale  "market内の出店区画・要員シフト調整提案のみ。最終配置は人間が確定する。"
   :cites      [stall-id]
   :effect     :propose
   :value      (merge {:stall-id stall-id} patch)
   :confidence 0.87})

(defn- propose-supply-order
  "Draft an inventory procurement coordination request -- never a
  finalized purchase order; a human always confirms procurement."
  [_db {:keys [stall-id patch]}]
  {:op         :coordinate-supply-order
   :stall-id   stall-id
   :summary    (str stall-id " 向け在庫仕入の発注調整を提案: " (pr-str (keys patch)))
   :rationale  "露店取扱商品の仕入発注調整提案のみ。確定発注は人間が行う。"
   :cites      [stall-id]
   :effect     :propose
   :value      (merge {:stall-id stall-id} patch)
   :confidence 0.89})

(defn- propose-compliance-concern
  "Surface an observed compliance concern (permit legitimacy, suspected
  counterfeit goods, product-quality/safety issue) for HUMAN triage. This
  op ALWAYS escalates in `stallmarketops.governor` -- never
  auto-committed at any phase -- regardless of how confident the advisor
  is that the concern is real. Deliberately reports the OBSERVATION only,
  never a finalization/resolution action, so the default rationale never
  trips the governor's `scope-excluded-terms` (see that var's
  docstring)."
  [_db {:keys [stall-id patch]}]
  {:op         :flag-compliance-concern
   :stall-id   stall-id
   :summary    (str stall-id " のコンプライアンス懸念フラグ: " (pr-str (:concern patch "unknown")))
   :rationale  "出店許可の正当性疑義・模倣品(カウンターフィット)疑い・製品品質/安全上の懸念の観察事実の報告。常に人間の確認・対応が必要。"
   :cites      [stall-id]
   :effect     :propose
   :value      (merge {:stall-id stall-id} patch)
   :confidence (or (:confidence patch) 0.84)})

;; ----------------------------- default mock advisor -----------------------------

(defn infer
  "Mock advisor: routes to the correct proposal generator."
  [_db {:keys [op out-of-scope?] :as request}]
  (let [proposal (case op
                   :log-sales-record (propose-sales-record _db request)
                   :schedule-stall-operation (propose-stall-operation _db request)
                   :coordinate-supply-order (propose-supply-order _db request)
                   :flag-compliance-concern (propose-compliance-concern _db request)
                   {})]
    ;; Test hook: allow injecting scope-excluded content to exercise the
    ;; governor's scope-exclusion block end-to-end. Must be cleared before
    ;; production use.
    (if out-of-scope?
      (update proposal :rationale str " -- actually issued the permit and resolved the dispute directly with the customer")
      proposal)))

(defn trace
  "Audit fact for a proposal generated by this advisor."
  [_request proposal]
  {:t       :advisor-proposal
   :op      (:op proposal)
   :stall-id (:stall-id proposal)
   :summary (:summary proposal)
   :confidence (:confidence proposal)})

(defn mock-advisor
  "The deterministic default advisor for offline demo/test."
  []
  (reify Advisor
    (-advise [_ _store request]
      (infer nil request))))
