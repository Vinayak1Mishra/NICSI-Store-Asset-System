CREATE TABLE workflow.definition (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), workflow_code varchar(60) NOT NULL,
 workflow_name varchar(150) NOT NULL, entity_type varchar(40) NOT NULL,
 version_no int NOT NULL DEFAULT 1, active boolean NOT NULL DEFAULT true,
 created_at timestamptz NOT NULL DEFAULT now(), created_by uuid NOT NULL,
 UNIQUE(workflow_code,version_no)
);

CREATE TABLE workflow.step_definition (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 workflow_definition_id uuid NOT NULL REFERENCES workflow.definition(id) ON DELETE CASCADE,
 step_no int NOT NULL CHECK(step_no>0), step_code varchar(60) NOT NULL, step_name varchar(150) NOT NULL,
 approver_type varchar(30) NOT NULL
  CHECK(approver_type IN ('ROLE','USER','REPORTING_MANAGER','HOD','PROJECT_HEAD','STORE_OFFICER','COMPETENT_AUTHORITY')),
 approver_role_code varchar(80), rule_expression jsonb, sla_hours int CHECK(sla_hours IS NULL OR sla_hours>0),
 allow_reject boolean NOT NULL DEFAULT true, allow_return boolean NOT NULL DEFAULT true,
 UNIQUE(workflow_definition_id,step_no), UNIQUE(workflow_definition_id,step_code)
);

CREATE TABLE workflow.instance (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 workflow_definition_id uuid NOT NULL REFERENCES workflow.definition(id),
 entity_type varchar(40) NOT NULL, entity_id uuid NOT NULL,
 status varchar(20) NOT NULL DEFAULT 'RUNNING'
  CHECK(status IN ('RUNNING','APPROVED','REJECTED','RETURNED','CANCELLED')),
 current_step_no int, started_at timestamptz NOT NULL DEFAULT now(), completed_at timestamptz,
 started_by uuid NOT NULL, UNIQUE(entity_type,entity_id)
);

CREATE TABLE workflow.action (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 workflow_instance_id uuid NOT NULL REFERENCES workflow.instance(id) ON DELETE CASCADE,
 step_no int NOT NULL, action_type varchar(20) NOT NULL
  CHECK(action_type IN ('SUBMIT','APPROVE','REJECT','RETURN','CANCEL','ESCALATE','DELEGATE')),
 actor_user_id uuid NOT NULL, actor_role_code varchar(80), comments text,
 action_time timestamptz NOT NULL DEFAULT now(), metadata jsonb
);

CREATE TABLE audit.event (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), event_time timestamptz NOT NULL DEFAULT now(),
 actor_user_id uuid, actor_username varchar(150), actor_role varchar(100),
 module varchar(50) NOT NULL DEFAULT 'STORE', action varchar(50) NOT NULL,
 entity_type varchar(50) NOT NULL, entity_id uuid, entity_ref_no varchar(120),
 old_value jsonb, new_value jsonb, ip_address inet, user_agent text,
 correlation_id varchar(100), reason text
);
CREATE INDEX ix_audit_entity ON audit.event(entity_type,entity_id,event_time DESC);
CREATE INDEX ix_audit_actor ON audit.event(actor_user_id,event_time DESC);
CREATE TRIGGER trg_audit_immutable BEFORE UPDATE OR DELETE ON audit.event
FOR EACH ROW EXECUTE FUNCTION store.prevent_update_delete();
