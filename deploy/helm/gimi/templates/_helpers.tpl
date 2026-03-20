{{- define "gimi.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "gimi.fullname" -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "gimi.labels" -}}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: gimi-cicd
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end -}}

{{- define "gimi.selectorLabels" -}}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "gimi.serverImage" -}}
{{- if .Values.global.imageRegistry -}}
{{- printf "%s/%s:%s" .Values.global.imageRegistry .Values.server.image.repository .Values.server.image.tag -}}
{{- else -}}
{{- printf "%s:%s" .Values.server.image.repository .Values.server.image.tag -}}
{{- end -}}
{{- end -}}

{{- define "gimi.workerImage" -}}
{{- if .Values.global.imageRegistry -}}
{{- printf "%s/%s:%s" .Values.global.imageRegistry .Values.worker.image.repository .Values.worker.image.tag -}}
{{- else -}}
{{- printf "%s:%s" .Values.worker.image.repository .Values.worker.image.tag -}}
{{- end -}}
{{- end -}}

{{- define "gimi.uiImage" -}}
{{- if .Values.global.imageRegistry -}}
{{- printf "%s/%s:%s" .Values.global.imageRegistry .Values.ui.image.repository .Values.ui.image.tag -}}
{{- else -}}
{{- printf "%s:%s" .Values.ui.image.repository .Values.ui.image.tag -}}
{{- end -}}
{{- end -}}

{{- define "gimi.secretName" -}}
{{- if .Values.secrets.existingSecret -}}
{{- .Values.secrets.existingSecret -}}
{{- else -}}
{{- include "gimi.fullname" . }}-secrets
{{- end -}}
{{- end -}}

{{- define "gimi.postgresUrl" -}}
{{- if .Values.postgresql.enabled -}}
jdbc:postgresql://{{ include "gimi.fullname" . }}-postgres:5432/gimi
{{- else -}}
{{- .Values.postgresql.external.url -}}
{{- end -}}
{{- end -}}

{{- define "gimi.redisUrl" -}}
{{- if .Values.redis.enabled -}}
redis://{{ include "gimi.fullname" . }}-redis:6379
{{- else -}}
{{- .Values.redis.external.url -}}
{{- end -}}
{{- end -}}
