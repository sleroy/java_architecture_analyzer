#!/bin/bash
# Script to add global AI instructions to all AI_ASSISTED prompts in migration phases
# This ensures consistent context and reduces token duplication

PHASES_DIR="migrations/ejb2spring/phases"
BACKUP_DIR="migrations/ejb2spring/phases/backup-$(date +%Y%m%d-%H%M%S)"

echo "========================================="
echo "Updating AI prompts with global instructions"
echo "========================================="
echo ""

# Create backup
mkdir -p "$BACKUP_DIR"
cp "$PHASES_DIR"/*.yaml "$BACKUP_DIR/"
echo "✓ Backup created: $BACKUP_DIR"
echo ""

# List of phases to update (excluding already updated phase2 and phase7)
PHASES_TO_UPDATE=(
  "phase0-assessment.yaml"
  "phase1-initialization.yaml"
  "phase3-cmp-entity-beans.yaml"
  "phase4-bmp-entity-beans.yaml"
  "phase5-stateful-session-beans.yaml"
  "phase6-message-driven-beans.yaml"
  "phase8-primary-key-classes.yaml"
  "phase9-jdbc-wrappers.yaml"
  "phase10-rest-apis.yaml"
  "phase11-soap-services.yaml"
  "phase12-antipatterns.yaml"
)

echo "Phases to update: ${#PHASES_TO_UPDATE[@]}"
echo ""

# For each phase file
for phase in "${PHASES_TO_UPDATE[@]}"; do
  file="$PHASES_DIR/$phase"
  
  if [ ! -f "$file" ]; then
    echo "⚠ Skipping $phase (not found)"
    continue
  fi
  
  echo "Processing: $phase"
  
  # Add ${ai_global_instructions} at the start of AI_ASSISTED and AI_ASSISTED_BATCH prompts
  # This is a simplified approach - manual review recommended
  sed -i 's/prompt: |$/prompt: |\n                ${ai_global_instructions}\n/' "$file"
  
  echo "✓ Updated: $phase"
done

echo ""
echo "========================================="
echo "✓ All phases updated"
echo "========================================="
echo ""
echo "Next steps:"
echo "1. Review changes: git diff $PHASES_DIR"
echo "2. Test migrations with updated plans"
echo "3. If issues, restore from: $BACKUP_DIR"
echo ""
