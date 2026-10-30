package com.jarvis.assistant.features

object FeatureRegistry {

    val CATEGORIES = listOf(
        FeatureCategoryItem("cat_context", "Context Radar", "radar", "Environment, movement & device context monitoring"),
        FeatureCategoryItem("cat_knowledge", "Personal Knowledge Fabric", "book", "Notes, decision history, sources & idea graphs"),
        FeatureCategoryItem("cat_creative", "Creative Instrument Lab", "palette", "Storytelling, design, audio, video & prompt workflows"),
        FeatureCategoryItem("cat_developer", "Developer Co-Pilot Bench", "code", "Build, security, testing, permissions & release tools"),
        FeatureCategoryItem("cat_device", "Device Lab", "phone", "Sensor, battery, thermal, audio & hardware diagnostics"),
        FeatureCategoryItem("cat_workflow", "Workflow Orchestration", "git", "Multi-step workflows with preview, approval & undo"),
        FeatureCategoryItem("cat_communication", "Communication Intelligence", "chat", "Messages, meeting notes, voice transcripts & follow-ups"),
        FeatureCategoryItem("cat_learning", "Learning & Skill Studio", "school", "Skill gaps, practice cards, quizzes & study plans"),
        FeatureCategoryItem("cat_safety", "Digital Safety & Trust", "shield", "Permission transparency, vault, session lock & safety"),
        FeatureCategoryItem("cat_forensics", "Information Forensics", "search", "Fact checking, claim timelines & document verification"),
        FeatureCategoryItem("cat_home", "Home & Environment", "home", "Inventory, appliance manuals, warranties & house notes"),
        FeatureCategoryItem("cat_mobility", "Mobility & Trip Intelligence", "map", "Travel, route decisions, packing & transit tools"),
        FeatureCategoryItem("cat_finance", "Finance & Resource Awareness", "money", "Budget tracking, receipts, subscriptions & renewal radar"),
        FeatureCategoryItem("cat_accessibility", "Accessibility & Inclusive UX", "accessibility", "Adaptive UI, plain language, dyslexia reader & contrast"),
        FeatureCategoryItem("cat_media", "Media & Content Craft", "movie", "Video, audio peaks, subtitles & publishing tools"),
        FeatureCategoryItem("cat_time", "Time & Attention Engineering", "clock", "Focus timers, attention recovery & task fragmentation"),
        FeatureCategoryItem("cat_offline", "Offline & Edge Intelligence", "offline", "Local AI models, offline OCR & edge grammar"),
        FeatureCategoryItem("cat_camera", "Camera & Visual Understanding", "camera", "Whiteboard scan, object checklists & visual tools"),
        FeatureCategoryItem("cat_notification", "Notification & Event Triage", "bell", "Alert digests, quiet hours & notification triage"),
        FeatureCategoryItem("cat_ecosystem", "App Ecosystem & Interoperability", "share", "Android intents, share routes & CalDAV/WebDAV sync"),
        FeatureCategoryItem("cat_emergency", "Emergency Preparedness", "alert", "Offline emergency cards, recovery kits & go-bags"),
        FeatureCategoryItem("cat_research", "Research & Publishing", "science", "Protocol builder, literature matrix & citations"),
        FeatureCategoryItem("cat_hud", "HUD & Menu Innovation", "dashboard", "Command dock, radial menu & HUD density dials"),
        FeatureCategoryItem("cat_project", "Project Automation", "briefcase", "Milestones, scope creep, risk registers & client updates"),
        FeatureCategoryItem("cat_privacy", "Privacy-Preserving Personalization", "lock", "Per-feature memory toggles, private vaults & data maps")
    )

    private val RAW_FEATURE_TITLES: Map<String, List<Pair<String, String>>> = mapOf(
        "Context Radar" to listOf(
            "Room acoustic fingerprint" to "None", "Pocket context switch" to "None",
            "Commute transition detector" to "ACCESS_FINE_LOCATION", "Ambient noise coach" to "None",
            "Light adaptation profile" to "Storage Access Framework", "Desk arrival ritual" to "None",
            "Quiet-zone memory" to "None", "Motion-safe dictation" to "RECORD_AUDIO",
            "Context confidence meter" to "None", "Environment snapshot" to "None",
            "Micro-routine discovery" to "None", "Focus boundary beacon" to "None",
            "Travel-mode handoff" to "ACCESS_FINE_LOCATION", "Battery-aware assistant" to "None",
            "Network mood indicator" to "INTERNET", "Privacy proximity pause" to "USE_BIOMETRIC",
            "Pocket accidental-tap guard" to "None", "Weather-to-task hint" to "None",
            "Local context timeline" to "None", "Context explain panel" to "None"
        ),
        "Personal Knowledge Fabric" to listOf(
            "Fact provenance tags" to "None", "Contradiction resolver" to "None",
            "Decision journal" to "None", "Knowledge expiry timer" to "None",
            "Personal glossary builder" to "None", "Source-to-claim map" to "None",
            "Question-gap finder" to "None", "Memory confidence slider" to "None",
            "Private entity graph" to "None", "Duplicate-idea merger" to "None",
            "Knowledge capsule export" to "Storage Access Framework", "Offline fact cards" to "None",
            "Memory review queue" to "None", "Learning-trail replay" to "None",
            "Concept bridge suggestions" to "None", "Personal SOP builder" to "None",
            "Terminology consistency check" to "Storage Access Framework", "Memory access audit" to "None",
            "Sensitive-note vault" to "USE_BIOMETRIC", "Knowledge snapshot diff" to "None"
        ),
        "Creative Instrument Lab" to listOf(
            "Voice-to-storyboard" to "RECORD_AUDIO", "Mood-to-color palette" to "None",
            "Dialogue rhythm coach" to "Storage Access Framework", "Scene continuity checker" to "None",
            "Idea mutation studio" to "None", "Soundscape recipe" to "RECORD_AUDIO",
            "Character voice bible" to "RECORD_AUDIO", "Thumbnail A/B notebook" to "CAMERA",
            "Prompt version diff" to "None", "Creative constraint dice" to "None",
            "Narrative tension map" to "None", "Brand voice consistency" to "RECORD_AUDIO",
            "Reference moodboard index" to "None", "Microcopy lab" to "None",
            "Tutorial shot planner" to "None", "Subtitle emotion markers" to "None",
            "Design-token extractor" to "None", "Alternate-ending forge" to "None",
            "Creative reuse finder" to "None", "Portfolio story composer" to "None"
        ),
        "Developer Co-Pilot Bench" to listOf(
            "Manifest permission diff" to "Storage Access Framework", "APK capability inspector" to "Storage Access Framework",
            "Build-failure timeline" to "None", "API contract notebook" to "Storage Access Framework",
            "Offline log redactor" to "Storage Access Framework", "Dependency drift watch" to "None",
            "UI state matrix" to "None", "Accessibility label auditor" to "CAMERA",
            "Deep-link route tester" to "ACCESS_FINE_LOCATION", "Database migration preview" to "None",
            "Secret-leak tripwire" to "None", "Release readiness gate" to "None",
            "Crash reproduction notebook" to "Storage Access Framework", "API latency diary" to "None",
            "Feature-flag console" to "None", "Test-data synthesizer" to "None",
            "Code ownership map" to "Storage Access Framework", "Permission justification writer" to "None",
            "Localization key auditor" to "None", "Rollback planner" to "None"
        ),
        "Device Lab" to listOf(
            "Thermal trend notebook" to "Storage Access Framework", "Storage churn detector" to "None",
            "Charging pattern coach" to "None", "Sensor drift lab" to "None",
            "Bluetooth device diary" to "BLUETOOTH_CONNECT", "Wi-Fi quality map" to "None",
            "App wakeup ledger" to "None", "Display burn-in reminder" to "None",
            "Microphone health test" to "RECORD_AUDIO", "Speaker channel check" to "None",
            "Camera focus calibration" to "CAMERA", "USB accessory notebook" to "Storage Access Framework",
            "Network handover log" to "INTERNET", "Battery-drain experiment" to "None",
            "App permission drift" to "None", "Doze compatibility test" to "None",
            "Audio-route inspector" to "RECORD_AUDIO", "Haptic pattern preview" to "None",
            "Refresh-rate observer" to "None", "Device health passport" to "None"
        ),
        "Workflow Orchestration" to listOf(
            "Two-phase action commit" to "None", "Undo-window scheduler" to "READ_CALENDAR",
            "Workflow dry run" to "None", "Human approval gate" to "None",
            "Branching routine canvas" to "None", "Failure-recovery branch" to "None",
            "Time-budget guard" to "None", "Network-cost gate" to "INTERNET",
            "Offline queue handoff" to "None", "Action receipt log" to "Storage Access Framework",
            "Routine conflict detector" to "None", "One-tap emergency stop" to "None",
            "Sensitive-step mask" to "None", "Schedule collision resolver" to "READ_CALENDAR",
            "Routine version history" to "None", "Cross-app handoff card" to "None",
            "Clipboard expiry workflow" to "None", "Form-fill review" to "None",
            "Transaction confirmation shield" to "None", "Workflow replay sandbox" to "None"
        ),
        "Communication Intelligence" to listOf(
            "Conversation promise tracker" to "None", "Reply tone meter" to "None",
            "Thread-context brief" to "None", "Meeting action extractor" to "READ_CALENDAR",
            "Contact alias resolver" to "READ_CONTACTS", "Follow-up timing coach" to "None",
            "Voice-note chaptering" to "RECORD_AUDIO", "Multilingual reply bridge" to "None",
            "Message attachment index" to "None", "Call preparation card" to "None",
            "Conversation boundary reminder" to "None", "Spam-pattern explainer" to "None",
            "Reply fact-check prompt" to "None", "Group decision ledger" to "None",
            "Pronunciation practice reply" to "RECORD_AUDIO", "Call debrief notes" to "None",
            "Message send delay" to "None", "Sensitive phrase warning" to "None",
            "Communication archive export" to "Storage Access Framework", "Availability phrase builder" to "None"
        ),
        "Learning & Skill Studio" to listOf(
            "Misconception detector" to "None", "Adaptive recall cards" to "None",
            "Skill-evidence portfolio" to "None", "Explain-back evaluator" to "None",
            "Concept prerequisite graph" to "None", "Exam-error taxonomy" to "None",
            "Micro-lesson composer" to "None", "Study-session energy log" to "Storage Access Framework",
            "Reference citation trainer" to "None", "Question ladder" to "None",
            "Learning-resource deduplicator" to "None", "Formula derivation notebook" to "Storage Access Framework",
            "Language-error notebook" to "Storage Access Framework", "Knowledge-transfer quiz" to "None",
            "Study-plan rescheduler" to "READ_CALENDAR", "Reading-difficulty lens" to "None",
            "Lab experiment planner" to "None", "Skill-decay alert" to "POST_NOTIFICATIONS",
            "Peer-review checklist" to "None", "Learning-outcome mapper" to "None"
        ),
        "Digital Safety & Trust" to listOf(
            "Permission why-now card" to "None", "Permission-expiry reminder" to "None",
            "Data-residency label" to "CAMERA", "Local-only mode seal" to "None",
            "Secret clipboard detector" to "None", "Export-preview redactor" to "Storage Access Framework",
            "Session-lock timer" to "USE_BIOMETRIC", "App-integrity note" to "None",
            "Link-destination explainer" to "None", "Permission-change alert" to "POST_NOTIFICATIONS",
            "Data-retention dial" to "None", "AI-source disclosure" to "None",
            "Model data boundary" to "None", "Biometric recheck gate" to "USE_BIOMETRIC",
            "Emergency data freeze" to "None", "Account-session inventory" to "None",
            "Phishing-clue trainer" to "None", "Secure-share expiry" to "USE_BIOMETRIC",
            "Privacy-incident notebook" to "Storage Access Framework", "Permission test mode" to "None"
        ),
        "Information Forensics" to listOf(
            "Claim timeline builder" to "None", "Screenshot-text provenance" to "CAMERA",
            "Source-reliability checklist" to "None", "Quote-context guard" to "None",
            "Image-edit clue review" to "CAMERA", "Document revision fingerprint" to "Storage Access Framework",
            "Fact-freshness badge" to "None", "Source-diversity meter" to "None",
            "Numerical-claim checker" to "None", "Citation-gap detector" to "None",
            "Rumor-spread map" to "None", "Archive snapshot note" to "Storage Access Framework",
            "Policy-change diff" to "None", "Translation-drift checker" to "None",
            "Data-table sanity check" to "None", "Chart-misleadingness review" to "None",
            "Fact-vs-opinion splitter" to "None", "Evidence-bundle maker" to "None",
            "Source-update watch" to "INTERNET", "Research-question refiner" to "Storage Access Framework"
        ),
        "Home & Environment" to listOf(
            "Appliance manual memory" to "Storage Access Framework", "Warranty clock" to "USE_BIOMETRIC",
            "Home-noise diary" to "None", "Room-comfort log" to "Storage Access Framework",
            "Maintenance evidence album" to "None", "Inventory location tags" to "ACCESS_FINE_LOCATION",
            "Utility-bill comparator" to "None", "Water-filter cycle tracker" to "None",
            "Plant microclimate diary" to "CAMERA", "Repair-part matcher" to "None",
            "Home emergency card" to "None", "Guest Wi-Fi share card" to "None",
            "Package-arrival ledger" to "None", "Room-setup blueprint" to "None",
            "Noise-sensitive schedule" to "READ_CALENDAR", "Cleaning rotation fairness" to "None",
            "Home-asset QR labels" to "CAMERA", "Power-outage timeline" to "None",
            "Moving-box index" to "None", "Home-project cost ledger" to "None"
        ),
        "Mobility & Trip Intelligence" to listOf(
            "Route decision journal" to "ACCESS_FINE_LOCATION", "Offline landmark notes" to "None",
            "Transit buffer calculator" to "None", "Trip-document expiry board" to "Storage Access Framework",
            "Packing-weight ledger" to "None", "Stopover task pocket" to "None",
            "Travel-expense split notes" to "ACCESS_FINE_LOCATION", "Local phrase quick deck" to "None",
            "Accessibility route notes" to "ACCESS_FINE_LOCATION", "Lost-item last-seen card" to "None",
            "Trip-change impact" to "None", "Fuel-stop ledger" to "None",
            "Vehicle-service forecast" to "None", "Parking memory pin" to "ACCESS_FINE_LOCATION",
            "Travel-connectivity plan" to "ACCESS_FINE_LOCATION", "Journey voice bookmark" to "RECORD_AUDIO",
            "Border-timezone planner" to "None", "Trip-safety check-in" to "None",
            "Baggage photo index" to "CAMERA", "Return-home reset" to "None"
        ),
        "Finance & Resource Awareness" to listOf(
            "Subscription renewal radar" to "None", "Cashflow what-if lab" to "None",
            "Receipt line-item notebook" to "Storage Access Framework", "Price memory graph" to "None",
            "Budget-drift explainer" to "None", "Shared-expense reconciler" to "None",
            "Bill-due collision alert" to "POST_NOTIFICATIONS", "Warranty-vs-repair calculator" to "Storage Access Framework",
            "Goal-funding simulator" to "None", "Spending-note voice capture" to "RECORD_AUDIO",
            "Financial-document vault" to "USE_BIOMETRIC", "Fee-breakdown reader" to "None",
            "Renewal-cancellation checklist" to "None", "Savings-milestone proof" to "None",
            "Currency-scenario compare" to "None", "Budget privacy mode" to "USE_BIOMETRIC",
            "Expense-anomaly questioner" to "None", "Purchase cooling-off timer" to "None",
            "Tax-document checklist" to "Storage Access Framework", "Resource-use scorecard" to "None"
        ),
        "Accessibility & Inclusive UX" to listOf(
            "Reading-focus ruler" to "None", "Plain-language switch" to "None",
            "Color-contrast preview" to "None", "Haptic navigation codes" to "Storage Access Framework",
            "Large-target audit" to "None", "Speech-pace controller" to "RECORD_AUDIO",
            "Caption-first mode" to "None", "One-hand reach map" to "None",
            "Dyslexia-friendly reader" to "None", "Switch-access shortcut deck" to "None",
            "Voice-command confirmation" to "RECORD_AUDIO", "Screen-magnifier handoff" to "None",
            "Audio-description notes" to "RECORD_AUDIO", "Language-specific number reader" to "None",
            "Gesture-alternative panel" to "None", "Focus-recovery prompt" to "None",
            "Accessible-error explainer" to "None", "Contrast auto test" to "None",
            "Voice-navigation landmark" to "RECORD_AUDIO", "Personal UX preference export" to "Storage Access Framework"
        ),
        "Media & Content Craft" to listOf(
            "Clip-continuity notebook" to "Storage Access Framework", "Audio-peak marker" to "RECORD_AUDIO",
            "Podcast fact cue" to "None", "Short-form hook tester" to "None",
            "Subtitle reading-speed meter" to "None", "Content-rights ledger" to "None",
            "Shot-match reference" to "CAMERA", "Thumbnail safe-area guide" to "CAMERA",
            "Audio-noise profile note" to "RECORD_AUDIO", "B-roll gap finder" to "None",
            "Export-preset memory" to "Storage Access Framework", "Content-calendar collision" to "READ_CALENDAR",
            "Narration breath marker" to "RECORD_AUDIO", "Media metadata sanitizer" to "Storage Access Framework",
            "Clip-naming assistant" to "None", "Podcast chapter draft" to "None",
            "Visual-consistency board" to "CAMERA", "Caption-variant lab" to "None",
            "Publishing checklist gate" to "None", "Media archive locator" to "Storage Access Framework"
        ),
        "Time & Attention Engineering" to listOf(
            "Task-fragmentation meter" to "None", "Attention-recovery recipe" to "None",
            "Deadline-risk radar" to "READ_CALENDAR", "Calendar-energy overlay" to "READ_CALENDAR",
            "Deep-work entry ritual" to "None", "Meeting-cost estimator" to "READ_CALENDAR",
            "Task-switching receipt" to "Storage Access Framework", "Unfinished-loop collector" to "None",
            "Deadline negotiation draft" to "READ_CALENDAR", "Calendar white-space guard" to "READ_CALENDAR",
            "Recurring-task variance" to "None", "Time-zone meeting bridge" to "READ_CALENDAR",
            "Focus-session evidence" to "None", "Priority tradeoff explainer" to "None",
            "Context-switch cooldown" to "None", "Personal SLA tracker" to "None",
            "Routine-friction finder" to "None", "Weekly-load forecast" to "None",
            "Task-dependency unblocker" to "USE_BIOMETRIC", "Time-audit export" to "Storage Access Framework"
        ),
        "Offline & Edge Intelligence" to listOf(
            "Offline command grammar" to "None", "Model-download integrity" to "None",
            "Edge-model switchboard" to "None", "Offline-queue inspector" to "None",
            "Local-search index builder" to "None", "On-device OCR batch" to "None",
            "Speech language pack manager" to "RECORD_AUDIO", "Inference-budget meter" to "None",
            "Private embedding store" to "None", "Offline translation cache" to "None",
            "Model-fallback explainer" to "None", "Edge-task prioritizer" to "None",
            "Offline form-draft queue" to "None", "Model-version rollback" to "None",
            "Local-data wipe proof" to "None", "Speech-confidence gate" to "RECORD_AUDIO",
            "Offline help manual" to "Storage Access Framework", "On-device redaction" to "None",
            "Compute thermal pause" to "None", "Edge capability report" to "None"
        ),
        "Camera & Visual Understanding" to listOf(
            "Object-to-checklist" to "CAMERA", "Whiteboard structure capture" to "CAMERA",
            "Cable-port identifier" to "None", "Visual measurement guide" to "CAMERA",
            "Document-edge repair" to "Storage Access Framework", "Scene-change bookmark" to "None",
            "Shelf-inventory scan" to "CAMERA", "Handwritten formula capture" to "None",
            "Color-sample notebook" to "CAMERA", "Visual checklist compare" to "CAMERA",
            "QR-action preview" to "CAMERA", "Text-to-object reminder" to "CAMERA",
            "Plant-change comparator" to "CAMERA", "Package-label reader" to "CAMERA",
            "White-balance reference" to "None", "Visual error-code reader" to "CAMERA",
            "Photo-privacy cropper" to "USE_BIOMETRIC", "Scene-annotation pins" to "None",
            "Visual-search notebook" to "CAMERA", "Camera-permission sandbox" to "CAMERA"
        ),
        "Notification & Event Triage" to listOf(
            "Notification meaning digest" to "POST_NOTIFICATIONS", "Alert-priority explainer" to "POST_NOTIFICATIONS",
            "Duplicate-alert grouper" to "POST_NOTIFICATIONS", "Quiet-hours exception list" to "POST_NOTIFICATIONS",
            "Notification-action memory" to "POST_NOTIFICATIONS", "Event-to-task bridge" to "None",
            "OTP exposure guard" to "POST_NOTIFICATIONS", "Delivery-alert timeline" to "POST_NOTIFICATIONS",
            "Calendar-invite conflict" to "READ_CALENDAR", "Alert-fatigue meter" to "POST_NOTIFICATIONS",
            "Silent-notification audit" to "POST_NOTIFICATIONS", "Notification-retention dial" to "POST_NOTIFICATIONS",
            "Critical-alert reconfirm" to "POST_NOTIFICATIONS", "App-channel tuner" to "None",
            "Notification privacy mask" to "USE_BIOMETRIC", "Reminder-collision merge" to "None",
            "Travel-alert filter" to "ACCESS_FINE_LOCATION", "Notification export summary" to "Storage Access Framework",
            "Notification-permission health" to "POST_NOTIFICATIONS", "Alert-rule simulator" to "POST_NOTIFICATIONS"
        ),
        "App Ecosystem & Interoperability" to listOf(
            "Intent compatibility catalog" to "Storage Access Framework", "Share-sheet smart route" to "ACCESS_FINE_LOCATION",
            "Calendar provider bridge" to "READ_CALENDAR", "Contacts data minimizer" to "READ_CONTACTS",
            "File-format handoff" to "Storage Access Framework", "Cross-app draft relay" to "None",
            "Deep-link safety preview" to "None", "Clipboard share confirmation" to "None",
            "WebDAV personal sync" to "INTERNET", "CalDAV event sync" to "INTERNET",
            "JARVIS NEXUS Remote Control" to "INTERNET", "Companion pairing QR" to "CAMERA",
            "Data-schema mapper" to "None", "Import duplicate resolver" to "Storage Access Framework",
            "External-app return handler" to "None", "Browser reading handoff" to "None",
            "Task-provider adapter" to "None", "Secure-token rotation reminder" to "USE_BIOMETRIC",
            "Connector-scope inspector" to "None", "Integration kill switch" to "None"
        ),
        "Emergency Preparedness" to listOf(
            "Personal emergency card" to "None", "Power-loss save guard" to "None",
            "Emergency contact draft" to "READ_CONTACTS", "Medical-info share choice" to "None",
            "Disaster checklist pack" to "None", "Offline meeting-point card" to "READ_CALENDAR",
            "Device recovery kit" to "None", "Emergency battery budget" to "None",
            "Network-outage message queue" to "INTERNET", "Document go-bag index" to "Storage Access Framework",
            "Evacuation route note" to "ACCESS_FINE_LOCATION", "Check-in timer" to "None",
            "Emergency sound beacon" to "None", "Offline emergency phrasebook" to "None",
            "Incident timeline recorder" to "None", "Family supply ledger" to "None",
            "Recovery contact tree" to "READ_CONTACTS", "Emergency privacy lock" to "USE_BIOMETRIC",
            "Safe-status template" to "None", "Post-incident checklist" to "None"
        ),
        "Research & Publishing" to listOf(
            "Research protocol builder" to "Storage Access Framework", "Literature theme matrix" to "None",
            "Evidence-strength ladder" to "None", "Research ethics prompt" to "Storage Access Framework",
            "Dataset dictionary maker" to "Storage Access Framework", "Reproducibility bundle" to "None",
            "Research claim trace" to "Storage Access Framework", "Abstract compression lab" to "None",
            "Peer-review response mapper" to "None", "Citation-style switcher" to "None",
            "Research-gap heatmap" to "Storage Access Framework", "Versioned research notebook" to "Storage Access Framework",
            "Publication checklist engine" to "None", "Figure-caption auditor" to "None",
            "Method completeness check" to "None", "Data-license reminder" to "None",
            "Research translation layer" to "Storage Access Framework", "Claim-limitations writer" to "None",
            "Research archive manifest" to "Storage Access Framework", "Knowledge-release gate" to "None"
        ),
        "HUD & Menu Innovation" to listOf(
            "Adaptive command dock" to "None", "Radial intent wheel" to "None",
            "Contextual mini-panel" to "None", "Voice waveform scrubber" to "RECORD_AUDIO",
            "Command explain drawer" to "None", "HUD density dial" to "None",
            "One-hand menu orbit" to "None", "Action undo toast" to "None",
            "Long-press action preview" to "None", "Menu search palette" to "None",
            "Feature pinboard" to "None", "Recent-action replay" to "None",
            "HUD focus ring" to "None", "Animated status grammar" to "None",
            "Voice-to-button suggestion" to "RECORD_AUDIO", "Feature permission badge" to "None",
            "Menu layout profiles" to "Storage Access Framework", "Command-history filter" to "None",
            "HUD safe-motion mode" to "None", "Feature discovery quest" to "None"
        ),
        "Project Automation" to listOf(
            "Project milestone radar" to "None", "Deliverable completeness gate" to "None",
            "Client brief diff" to "None", "Revision-request parser" to "None",
            "Asset naming guard" to "Storage Access Framework", "Project decision freeze" to "None",
            "Dependency wait monitor" to "None", "Handover package builder" to "None",
            "Project risk register" to "None", "Scope-creep meter" to "None",
            "Milestone evidence capture" to "None", "Project cost-to-complete" to "None",
            "Client update composer" to "None", "Deliverable version lock" to "USE_BIOMETRIC",
            "Project retrospective builder" to "None", "Reusable workflow template" to "None",
            "Project permission matrix" to "None", "Deadline-change impact" to "READ_CALENDAR",
            "Project archive integrity" to "Storage Access Framework", "Project resume brief" to "None"
        ),
        "Privacy-Preserving Personalization" to listOf(
            "Preference confidence decay" to "None", "Per-feature memory toggle" to "None",
            "Temporary persona session" to "None", "Sensitive-topic pause" to "None",
            "Memory preview before save" to "None", "Local preference learning" to "None",
            "Personalization reset receipt" to "Storage Access Framework", "Shared-device guest mode" to "None",
            "Session-only search" to "None", "Data-use preview" to "None",
            "Selective preference export" to "Storage Access Framework", "Memory source review" to "None",
            "Personalization explain-why" to "None", "Temporary context expiry" to "None",
            "Privacy profile switch" to "USE_BIOMETRIC", "Model opt-out routing" to "None",
            "Private prompt locker" to "USE_BIOMETRIC", "Memory correction workflow" to "None",
            "Personal data map" to "None", "Personalization audit trail" to "None"
        )
    )

    val ALL_FEATURES: List<JarvisFeatureItem> by lazy {
        val list = mutableListOf<JarvisFeatureItem>()
        var globalIndex = 1
        RAW_FEATURE_TITLES.forEach { (categoryName, featurePairs) ->
            featurePairs.forEach { (title, perm) ->
                val idStr = "feat_${globalIndex.toString().padStart(3, '0')}"
                val risk = when (perm) {
                    "USE_BIOMETRIC", "ACCESS_FINE_LOCATION" -> FeatureRiskLevel.HIGH
                    "RECORD_AUDIO", "CAMERA", "READ_CONTACTS", "READ_CALENDAR", "POST_NOTIFICATIONS" -> FeatureRiskLevel.MEDIUM
                    else -> FeatureRiskLevel.LOW
                }
                val desc = "JARVIS $title capability for $categoryName. Supports interactive preview, local execution, status toggling, and audit log history."
                list.add(
                    JarvisFeatureItem(
                        id = idStr,
                        title = title,
                        category = categoryName,
                        description = desc,
                        requiredPermission = perm,
                        riskLevel = risk,
                        routeKey = idStr
                    )
                )
                globalIndex++
            }
        }
        list
    }

    fun getFeaturesForCategory(categoryName: String): List<JarvisFeatureItem> {
        return ALL_FEATURES.filter { it.category.equals(categoryName, ignoreCase = true) }
    }

    fun searchFeatures(query: String): List<JarvisFeatureItem> {
        if (query.isBlank()) return ALL_FEATURES
        val lower = query.trim().lowercase()
        return ALL_FEATURES.filter {
            it.title.lowercase().contains(lower) ||
                    it.category.lowercase().contains(lower) ||
                    it.requiredPermission.lowercase().contains(lower) ||
                    it.description.lowercase().contains(lower)
        }
    }
}
