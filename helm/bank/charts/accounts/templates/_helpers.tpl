{{/*
Common labels applied to every object.
*/}}
{{- define "accounts.labels" -}}
app.kubernetes.io/name: accounts
app.kubernetes.io/component: microservice
app.kubernetes.io/part-of: bank
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{/*
Selector labels (stable subset used by Service selector and Deployment matchLabels).
*/}}
{{- define "accounts.selectorLabels" -}}
app.kubernetes.io/name: accounts
app.kubernetes.io/component: microservice
{{- end -}}

{{/*
Fully qualified image reference. image.tag falls back to global.image.tag.
*/}}
{{- define "accounts.image" -}}
{{- $tag := .Values.image.tag | default .Values.global.image.tag -}}
{{- printf "%s:%s" .Values.image.repository $tag -}}
{{- end -}}
