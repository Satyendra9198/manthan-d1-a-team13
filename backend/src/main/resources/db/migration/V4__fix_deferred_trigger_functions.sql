-- Fix: in PL/pgSQL a CASE expression referencing NEW.<col> resolves BOTH branches, so
-- "CASE WHEN TG_TABLE_NAME = 'refunds' THEN NEW.id ELSE NEW.refund_id END" fails on the refunds table
-- ("record new has no field refund_id"). Use IF/ELSE so only the matching field is read. [BR-13][BR-18]

CREATE OR REPLACE FUNCTION rr_refund_items_sum() RETURNS trigger AS $$
DECLARE v_refund uuid; v_amount bigint; v_sum bigint;
BEGIN
  IF TG_TABLE_NAME = 'refunds' THEN
    v_refund := NEW.id;
  ELSE
    v_refund := NEW.refund_id;
  END IF;
  SELECT amount_paise INTO v_amount FROM refunds WHERE id = v_refund;
  SELECT COALESCE(SUM(amount_paise), 0) INTO v_sum FROM refund_items WHERE refund_id = v_refund;
  IF v_amount IS DISTINCT FROM v_sum THEN
    RAISE EXCEPTION 'refund % amount % <> sum of items %', v_refund, v_amount, v_sum
      USING ERRCODE = 'check_violation', CONSTRAINT = 'trg_refund_items_sum';
  END IF;
  RETURN NULL;
END $$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION rr_ledger_balanced() RETURNS trigger AS $$
DECLARE v_txn bigint; v_debit bigint; v_credit bigint; v_lines int;
BEGIN
  IF TG_TABLE_NAME = 'ledger_transactions' THEN
    v_txn := NEW.id;
  ELSE
    v_txn := NEW.transaction_id;
  END IF;
  SELECT COALESCE(SUM(amount_paise) FILTER (WHERE direction = 'DEBIT'), 0),
         COALESCE(SUM(amount_paise) FILTER (WHERE direction = 'CREDIT'), 0), COUNT(*)
    INTO v_debit, v_credit, v_lines FROM ledger_entries WHERE transaction_id = v_txn;
  IF v_lines < 2 OR v_debit <> v_credit THEN
    RAISE EXCEPTION 'ledger transaction % unbalanced (debit %, credit %, lines %)', v_txn, v_debit, v_credit, v_lines
      USING ERRCODE = 'check_violation', CONSTRAINT = 'trg_ledger_balanced';
  END IF;
  RETURN NULL;
END $$ LANGUAGE plpgsql;
