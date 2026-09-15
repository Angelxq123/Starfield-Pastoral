package com.stardew.craft.npc.data;


import java.util.Map;

/**
 * Validates cross-file data completeness for implemented NPCs.
 */
public final class NpcDataDiagnostics {
    private static final org.slf4j.Logger LOGGER=com.mojang.logging.LogUtils.getLogger();
    private NpcDataDiagnostics() {
    }

    public static void validateAndLog(Map<String, NpcCapabilityProfile> capabilities,
                                      Map<String, com.google.gson.JsonObject> dialogues,
                                      Map<String, com.google.gson.JsonObject> schedules,
                                      Map<String, com.google.gson.JsonObject> tastes) {
        int missingDialogues=0,missingSchedules=0,missingTastes=0,unboundActors=0;
        for (NpcCapabilityProfile capability : capabilities.values()) {
            if (!capability.implemented()) {
                continue;
            }

            String npcId = capability.npcId();
            if (!dialogues.containsKey(npcId)) {
                missingDialogues++;
                LOGGER.debug("[NPC_DATA] npc={} missing dialogue document",npcId);
            }
            if (!schedules.containsKey(npcId)) {
                missingSchedules++;
                LOGGER.debug("[NPC_DATA] npc={} has no autonomous schedule (domain-owned actors may be intentional)",npcId);
            }
            if (!tastes.containsKey(npcId)) {
                missingTastes++;
                LOGGER.debug("[NPC_DATA] npc={} missing gift-taste document",npcId);
            }
            var schedule=schedules.get(npcId);
            if(schedule!=null) {
                var missing=new java.util.TreeSet<String>();
                var missingAreas=new java.util.TreeSet<String>();
                for(var nodes:NpcScheduleCompiler.compile(schedule).values()) for(var node:nodes) {
                    String behavior=node.behavior();
                    if(com.stardew.craft.npc.runtime.NpcSquareMovement.parse(behavior)!=null) {
                        if(com.stardew.craft.npc.runtime.NpcSquareArea.forPoint(node.point())==null)missingAreas.add(node.point());
                        continue;
                    }
                    if(!behavior.isBlank() && !behavior.startsWith("dialogue") && NpcActivityCatalog.find(npcId,behavior)==null)
                        missing.add(behavior);
                }
                if(!missingAreas.isEmpty())LOGGER.debug("[NPC_DATA] npc={} square behaviors need confirmed MC areas at points={}",npcId,missingAreas);
                if(!missing.isEmpty()) {
                    unboundActors++;
                    LOGGER.debug("[NPC_DATA] npc={} unbound behaviors={} (no activity will play)",npcId,missing);
                }
            }
        }
        LOGGER.info("[NPC_DATA] Content summary: missing dialogue={}, schedule={}, gift tastes={}; actors with unbound behaviors={}. Details at DEBUG; content gaps do not reject loading.",
                missingDialogues,missingSchedules,missingTastes,unboundActors);
    }
}
