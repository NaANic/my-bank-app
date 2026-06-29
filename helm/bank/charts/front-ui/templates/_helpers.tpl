{{- define "front-ui.labels" -}}
app.kubernetes.io/name: front-ui
app.kubernetes.io/part-of: bank
{{- end -}}

{{- define "front-ui.selectorLabels" -}}
app.kubernetes.io/name: front-ui
{{- end -}}

{{- define "front-ui.image" -}}
{{- $tag := .Values.image.tag | default .Values.global.image.tag -}}
{{- printf "%s:%s" .Values.image.repository $tag -}}
{{- end -}}
