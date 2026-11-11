#!/usr/bin/env bash
# Uploads the Paper/Purpur/Folia/Spigot plugin jar to PaperMC Hangar (https://hangar.papermc.io).
#
# Usage: hangar-upload.sh <project slug or Author/Slug> <jar> <version> <channel> <notes.md> <game versions...>
# Needs HANGAR_TOKEN (an API key with the create_version permission).
set -euo pipefail

project="$1"; jar="$2"; version="$3"; raw_channel="$4"; notes="$5"; shift 5
wanted=("$@")

api="https://hangar.papermc.io/api/v1"
agent="Shamanalle/mob-senses (GitHub Actions)"

case "${raw_channel,,}" in
  alpha) channel="Alpha" ;;
  beta)  channel="Beta" ;;
  *)     channel="Release" ;;
esac

clean_key=$(printf '%s' "${HANGAR_TOKEN}" | tr -d '[:space:]')
enc_key=$(jq -rn --arg v "${clean_key}" '$v | @uri')
status=$(curl -sS -o auth.json -w '%{http_code}' -X POST -H "User-Agent: ${agent}" \
  "${api}/authenticate?apiKey=${enc_key}" || echo 000)
if [ "${status}" != "200" ]; then
  echo "::error::Hangar authentication answered ${status}: $(head -c 400 auth.json 2>/dev/null)"
  exit 1
fi
jwt=$(jq -r '.token // ""' auth.json)
rm -f auth.json
if [ -z "${jwt}" ]; then
  echo "::error::Hangar returned an empty JWT"
  exit 1
fi

status=$(curl -sS -o hangar-versions.json -w '%{http_code}' -H "User-Agent: ${agent}" \
  "${api}/platforms/PAPER/versions" || echo 000)
if [ "${status}" != "200" ]; then
  echo "::error::Hangar PAPER versions answered ${status}: $(head -c 400 hangar-versions.json 2>/dev/null)"
  exit 1
fi

known=$(jq -c '[.[].version, (.[] | .subVersions[]?)] | unique' hangar-versions.json)
matched=()
for v in "${wanted[@]}"; do
  if jq -e --arg v "${v}" 'index($v) != null' <<< "${known}" > /dev/null; then
    matched+=("${v}")
  else
    short=$(cut -d. -f1-2 <<< "${v}")
    if jq -e --arg v "${short}" 'index($v) != null' <<< "${known}" > /dev/null; then
      matched+=("${short}")
    else
      echo "::warning::Hangar PAPER has no game version ${v}"
    fi
  fi
done
if [ "${#matched[@]}" -eq 0 ]; then
  echo "::error::None of the game versions matched Hangar's PAPER list"
  exit 1
fi
paper_versions=$(printf '%s\n' "${matched[@]}" | jq -R . | jq -s 'unique')

svc_deps='{"PAPER":[
  {"name":"SimpleVoiceChat","projectId":400,"required":false,"externalUrl":null,"platform":"PAPER"}
]}'

for deps in "${svc_deps}" '{}'; do
  jq -n \
    --arg version "${version}" \
    --arg channel "${channel}" \
    --rawfile description "${notes}" \
    --argjson paper "${paper_versions}" \
    --argjson pluginDeps "${deps}" \
    '{
      version: $version,
      channel: $channel,
      description: $description,
      files: [{platforms: ["PAPER"], externalUrl: null}],
      platformDependencies: {PAPER: $paper},
      pluginDependencies: $pluginDeps
    }' > hangar-upload.json

  status=$(curl -sS -o hangar-reply.json -w '%{http_code}' \
    -H "Authorization: HangarAuth ${jwt}" \
    -H "User-Agent: ${agent}" \
    -F "versionUpload=<hangar-upload.json;type=application/json" \
    -F "files=@${jar};type=application/java-archive" \
    "${api}/projects/${project}/upload" || echo 000)

  if [ "${status}" = "200" ] || [ "${status}" = "201" ]; then
    echo "Uploaded to Hangar (${project}): $(jq -r '.url // empty' hangar-reply.json 2>/dev/null || echo "${version}")"
    exit 0
  fi
  echo "Hangar upload answered ${status}: $(head -c 600 hangar-reply.json 2>/dev/null)"
  if [ "${status}" != "400" ]; then
    break
  fi
done

echo "::error::The plugin could not be uploaded to Hangar project ${project}"
exit 1
