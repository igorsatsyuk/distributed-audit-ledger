{{- define "dal.name" -}}
distributed-audit-ledger
{{- end -}}

{{- define "dal.fullname" -}}
{{- printf "%s-%s" .Release.Name "dal" | trunc 63 | trimSuffix "-" -}}
{{- end -}}


{{- define "dal.namespace" -}}
{{- default .Release.Namespace .Values.namespaceOverride -}}
{{- end -}}

{{- define "dal.configName" -}}{{ printf "%s-config" (include "dal.fullname" .) | trunc 63 | trimSuffix "-" }}{{- end -}}
{{- define "dal.secretsName" -}}{{ printf "%s-secrets" (include "dal.fullname" .) | trunc 63 | trimSuffix "-" }}{{- end -}}
{{- define "dal.postgresName" -}}{{ printf "%s-postgres" (include "dal.fullname" .) | trunc 63 | trimSuffix "-" }}{{- end -}}
{{- define "dal.zookeeperName" -}}{{ printf "%s-zookeeper" (include "dal.fullname" .) | trunc 63 | trimSuffix "-" }}{{- end -}}
{{- define "dal.kafkaName" -}}{{ printf "%s-kafka" (include "dal.fullname" .) | trunc 63 | trimSuffix "-" }}{{- end -}}
{{- define "dal.commandServiceName" -}}{{ printf "%s-command-service" (include "dal.fullname" .) | trunc 63 | trimSuffix "-" }}{{- end -}}
{{- define "dal.eventStoreServiceName" -}}{{ printf "%s-event-store-service" (include "dal.fullname" .) | trunc 63 | trimSuffix "-" }}{{- end -}}
{{- define "dal.auditWriterServiceName" -}}{{ printf "%s-audit-writer-service" (include "dal.fullname" .) | trunc 63 | trimSuffix "-" }}{{- end -}}
{{- define "dal.queryServiceName" -}}{{ printf "%s-query-service" (include "dal.fullname" .) | trunc 63 | trimSuffix "-" }}{{- end -}}
{{- define "dal.auditUiName" -}}{{ printf "%s-audit-ui" (include "dal.fullname" .) | trunc 63 | trimSuffix "-" }}{{- end -}}
{{- define "dal.ingressName" -}}{{ printf "%s-ingress" (include "dal.fullname" .) | trunc 63 | trimSuffix "-" }}{{- end -}}

