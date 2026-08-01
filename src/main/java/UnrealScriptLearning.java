import java.util.LinkedHashMap;
import java.util.Map;

/** Curated UE1 learning material focused on BunnyTrack mapping scripts. */
public final class UnrealScriptLearning {
    public static final String[] TEMPLATES = {
            "Basic Actor", "BT Message Trigger", "BT Launch Pad", "Timed Trigger", "Mutator"
    };
    public static final String[] LESSONS = {
            "1. Class structure", "2. Events and functions", "3. defaultproperties",
            "4. Touch trigger", "5. Launch pad", "6. Timers and events",
            "7. Movers and map events", "8. Networking basics", "9. Package compilation"
    };

    private static final Map<String, String> TEMPLATE_SOURCE = new LinkedHashMap<>();
    private static final Map<String, String> LESSON_HTML = new LinkedHashMap<>();
    private static final Map<String, String> LESSON_SOURCE = new LinkedHashMap<>();

    static {
        TEMPLATE_SOURCE.put("Basic Actor", """
                class MyBTActor expands Actor;

                event PostBeginPlay()
                {
                    Super.PostBeginPlay();
                }

                defaultproperties
                {
                    bHidden=False
                    RemoteRole=ROLE_None
                }
                """);
        TEMPLATE_SOURCE.put("BT Message Trigger", """
                class BTMessageTrigger expands Triggers;

                var() localized string PlayerMessage;
                var() bool bTriggerOnceOnly;
                var bool bAlreadyTriggered;

                function Touch(Actor Other)
                {
                    local PlayerPawn Player;

                    Player = PlayerPawn(Other);
                    if (Player == None || (bTriggerOnceOnly && bAlreadyTriggered))
                        return;

                    Player.ClientMessage(PlayerMessage);
                    TriggerEvent(Event, Self, Player);
                    bAlreadyTriggered = True;
                }

                defaultproperties
                {
                    PlayerMessage="Checkpoint reached"
                    bTriggerOnceOnly=True
                    bHidden=True
                    bCollideActors=True
                    CollisionRadius=40.000000
                    CollisionHeight=40.000000
                    RemoteRole=ROLE_None
                }
                """);
        TEMPLATE_SOURCE.put("BT Launch Pad", """
                class BTLaunchPad expands Triggers;

                var() float ForwardSpeed;
                var() float UpwardSpeed;

                function Touch(Actor Other)
                {
                    local Pawn Player;
                    local Vector LaunchVelocity;

                    Player = Pawn(Other);
                    if (Player == None)
                        return;

                    LaunchVelocity = Vector(Rotation) * ForwardSpeed;
                    LaunchVelocity.Z = UpwardSpeed;
                    Player.SetPhysics(PHYS_Falling);
                    Player.Velocity = LaunchVelocity;
                    TriggerEvent(Event, Self, Player);
                }

                defaultproperties
                {
                    ForwardSpeed=900.000000
                    UpwardSpeed=500.000000
                    bHidden=True
                    bCollideActors=True
                    CollisionRadius=48.000000
                    CollisionHeight=16.000000
                    RemoteRole=ROLE_None
                }
                """);
        TEMPLATE_SOURCE.put("Timed Trigger", """
                class BTDelayedTrigger expands Triggers;

                var() float DelaySeconds;
                var Pawn TriggeringPlayer;

                function Trigger(Actor Other, Pawn EventInstigator)
                {
                    TriggeringPlayer = EventInstigator;
                    SetTimer(DelaySeconds, False);
                }

                function Timer()
                {
                    TriggerEvent(Event, Self, TriggeringPlayer);
                    TriggeringPlayer = None;
                }

                defaultproperties
                {
                    DelaySeconds=1.000000
                    bHidden=True
                    RemoteRole=ROLE_None
                }
                """);
        TEMPLATE_SOURCE.put("Mutator", """
                class BTUtilityMutator expands Mutator;

                function ModifyPlayer(Pawn Other)
                {
                    Super.ModifyPlayer(Other);
                    // Apply server-authoritative player setup here.
                }

                defaultproperties
                {
                }
                """);

        LESSON_SOURCE.put("1. Class structure", """
                class MyBTActor expands Actor;

                defaultproperties
                {
                    bHidden=False
                    RemoteRole=ROLE_None
                }
                """);
        LESSON_SOURCE.put("2. Events and functions", """
                class BTEventExample expands Triggers;

                event PostBeginPlay()
                {
                    Super.PostBeginPlay();
                }

                function Trigger(Actor Other, Pawn EventInstigator)
                {
                    TriggerEvent(Event, Self, EventInstigator);
                }

                defaultproperties
                {
                    bHidden=True
                }
                """);
        LESSON_SOURCE.put("3. defaultproperties", """
                class BTDefaultsExample expands Triggers;

                defaultproperties
                {
                    bHidden=True
                    bCollideActors=True
                    CollisionRadius=40.000000
                    CollisionHeight=40.000000
                    RemoteRole=ROLE_None
                }
                """);
        LESSON_SOURCE.put("4. Touch trigger", TEMPLATE_SOURCE.get("BT Message Trigger"));
        LESSON_SOURCE.put("5. Launch pad", TEMPLATE_SOURCE.get("BT Launch Pad"));
        LESSON_SOURCE.put("6. Timers and events", TEMPLATE_SOURCE.get("Timed Trigger"));
        LESSON_SOURCE.put("7. Movers and map events", """
                class BTMoverEventTrigger expands Triggers;

                function Trigger(Actor Other, Pawn EventInstigator)
                {
                    TriggerEvent(Event, Self, EventInstigator);
                }

                defaultproperties
                {
                    bHidden=True
                    RemoteRole=ROLE_None
                }
                """);
        LESSON_SOURCE.put("8. Networking basics", """
                class BTNetworkHelper expands Actor;

                simulated function PostNetBeginPlay()
                {
                    Super.PostNetBeginPlay();
                }

                defaultproperties
                {
                    RemoteRole=ROLE_SimulatedProxy
                }
                """);
        LESSON_SOURCE.put("9. Package compilation", """
                class BTPackageExample expands Actor;

                // Save as BTPackageExample.uc in BTTools/Classes.
                defaultproperties
                {
                    bHidden=True
                    RemoteRole=ROLE_None
                }
                """);

        LESSON_HTML.put("1. Class structure", page("Class structure", """
                <p>Every <code>.uc</code> file contains one class. Its file name must match the class name.</p>
                <pre>class BTMessageTrigger expands Triggers;</pre>
                <p><code>expands</code> and <code>extends</code> both express inheritance in classic
                UnrealScript. Inherited variables and functions determine what your class can use.</p>
                <h3>Exercise</h3>
                <p>Create <code>MyBTActor.uc</code>, inherit from <code>Actor</code>, and add an empty
                <code>defaultproperties</code> block.</p>
                """));
        LESSON_HTML.put("2. Events and functions", page("Events and functions", """
                <p>Events are functions called by the engine. BunnyTrack utility actors commonly use:</p>
                <ul><li><code>PostBeginPlay()</code> — initialization after spawning</li>
                <li><code>Touch(Actor Other)</code> — another actor enters the collision cylinder</li>
                <li><code>UnTouch(Actor Other)</code> — it leaves the cylinder</li>
                <li><code>Trigger(Actor Other, Pawn EventInstigator)</code> — a matching event fires</li>
                <li><code>Timer()</code> — a timer created by <code>SetTimer</code> expires</li></ul>
                <pre>function Touch(Actor Other)
{
    local Pawn Player;
    Player = Pawn(Other);
    if (Player == None)
        return;
}</pre>
                <p>Always validate a cast before accessing it. Otherwise UT logs an
                <code>Accessed None</code> runtime warning.</p>
                """));
        LESSON_HTML.put("3. defaultproperties", page("defaultproperties", """
                <p>This block supplies initial values. It is not an event and it does not contain
                normal statements. Assignments normally have no semicolon.</p>
                <pre>defaultproperties
{
    bHidden=True
    bCollideActors=True
    CollisionRadius=40.000000
    CollisionHeight=40.000000
    RemoteRole=ROLE_None
}</pre>
                <p>Available properties come from the class and all its parents. Trigger-like actors
                need collision enabled for <code>Touch()</code> to run.</p>
                """));
        LESSON_HTML.put("4. Touch trigger", page("Touch trigger", """
                <p>A map helper can react when a player touches its collision cylinder. Cast
                <code>Other</code> to <code>PlayerPawn</code> or <code>Pawn</code>, check for
                <code>None</code>, then perform the action.</p>
                <pre>Player = PlayerPawn(Other);
if (Player == None)
    return;
Player.ClientMessage("Checkpoint reached");
TriggerEvent(Event, Self, Player);</pre>
                <p><code>TriggerEvent</code> activates actors whose <code>Tag</code> matches this
                actor's <code>Event</code>. This is the main bridge between script and map setup.</p>
                """));
        LESSON_HTML.put("5. Launch pad", page("Launch pad", """
                <p>A launch pad changes a Pawn's velocity. Use the actor's rotation for its forward
                direction and a separate vertical component.</p>
                <pre>LaunchVelocity = Vector(Rotation) * ForwardSpeed;
LaunchVelocity.Z = UpwardSpeed;
Player.SetPhysics(PHYS_Falling);
Player.Velocity = LaunchVelocity;</pre>
                <p>Rotate the placed actor in UnrealEd to control launch direction. Expose tuning
                values with <code>var()</code> so mappers can edit them in Actor Properties.</p>
                """));
        LESSON_HTML.put("6. Timers and events", page("Timers and events", """
                <p><code>SetTimer(Delay, False)</code> schedules one <code>Timer()</code> call.
                Passing <code>True</code> repeats it. Store the instigator if it is needed later.</p>
                <pre>function Trigger(Actor Other, Pawn EventInstigator)
{
    TriggeringPlayer = EventInstigator;
    SetTimer(1.0, False);
}

function Timer()
{
    TriggerEvent(Event, Self, TriggeringPlayer);
}</pre>
                <p>An Actor has one standard timer. Starting it again replaces the previous timer.</p>
                """));
        LESSON_HTML.put("7. Movers and map events", page("Movers and map events", """
                <p>Many BunnyTrack mechanisms need no custom code. Connect stock actors using
                <code>Event</code> and <code>Tag</code>:</p>
                <ol><li>Give the Mover a unique <code>Tag</code>.</li>
                <li>Give the Trigger the same value as its <code>Event</code>.</li>
                <li>Choose the Mover's trigger behavior and keyframes in UnrealEd.</li></ol>
                <p>Write a custom class only when stock Trigger, Dispatcher, SpecialEvent,
                Counter, Mover, Teleporter, ZoneInfo, or Kicker behavior is insufficient.</p>
                """));
        LESSON_HTML.put("8. Networking basics", page("Networking basics", """
                <p>Gameplay authority belongs to the server. Changes that affect completion,
                player state, timing, or movers should be made on the authoritative instance.</p>
                <ul><li><code>ROLE_None</code>: no replication; suitable for server-only helpers.</li>
                <li><code>ROLE_SimulatedProxy</code>: client predicts an actor such as a projectile.</li>
                <li><code>simulated</code>: permits client execution; it does not send changes to server.</li></ul>
                <p>Test with a separate local server and client. Standalone play can hide replication bugs.</p>
                """));
        LESSON_HTML.put("9. Package compilation", page("Package compilation", """
                <p>Use this directory layout:</p>
                <pre>UnrealTournament/
  BTTools/
    Classes/
      BTLaunchPad.uc
  System/
    ucc.exe
    UnrealTournament.ini</pre>
                <p>Add <code>EditPackages=BTTools</code> under
                <code>[Editor.EditorEngine]</code>, then run <code>ucc make</code> from System.
                The output is <code>BTTools.u</code>.</p>
                <p>If an old package prevents rebuilding, back it up and remove it deliberately
                before compiling. The assistant never deletes compiled packages automatically.</p>
                """));
    }

    private UnrealScriptLearning() { }

    public static String template(String name) {
        return TEMPLATE_SOURCE.getOrDefault(name, TEMPLATE_SOURCE.get("Basic Actor"));
    }

    public static String lesson(String name) {
        return LESSON_HTML.getOrDefault(name, LESSON_HTML.get("1. Class structure"));
    }

    public static String exampleForLesson(String lesson) {
        return LESSON_SOURCE.getOrDefault(lesson, LESSON_SOURCE.get("1. Class structure"));
    }

    public static String templateForLesson(String lesson) {
        if (lesson == null) return "Basic Actor";
        if (lesson.startsWith("4.")) return "BT Message Trigger";
        if (lesson.startsWith("5.")) return "BT Launch Pad";
        if (lesson.startsWith("6.") || lesson.startsWith("7.")) return "Timed Trigger";
        if (lesson.startsWith("8.")) return "Mutator";
        return "Basic Actor";
    }

    public static String referenceFor(String parentClass) {
        String parent = parentClass == null ? "Actor" : parentClass;
        String contextual = switch (parent.toLowerCase()) {
            case "trigger", "triggers" -> """
                    <h3>Useful trigger properties</h3>
                    <pre>Event=TargetTag
bHidden=True
bCollideActors=True
CollisionRadius=40.000000
CollisionHeight=40.000000
RemoteRole=ROLE_None</pre>
                    <h3>Useful events</h3>
                    <p><code>Touch</code>, <code>UnTouch</code>, <code>Trigger</code>,
                    <code>UnTrigger</code>, <code>Timer</code></p>
                    """;
            case "zoneinfo" -> """
                    <h3>Useful zone properties</h3>
                    <pre>ZoneGravity=(Z=-950.000000)
ZoneVelocity=(X=0.000000,Y=0.000000,Z=0.000000)
bPainZone=False
DamagePerSec=0
DamageType=None</pre>
                    <h3>Useful events</h3>
                    <p><code>ActorEntered</code> and <code>ActorLeaving</code></p>
                    """;
            case "mutator" -> """
                    <h3>Useful mutator functions</h3>
                    <p><code>ModifyPlayer</code>, <code>CheckReplacement</code>,
                    <code>AlwaysKeep</code>, <code>MutatorTakeDamage</code></p>
                    <p>Call the corresponding <code>Super</code> function unless you intentionally
                    replace the inherited mutator chain behavior.</p>
                    """;
            default -> """
                    <h3>Common Actor properties</h3>
                    <pre>Tag=MyTag
Event=TargetTag
bHidden=False
RemoteRole=ROLE_None
LifeSpan=0.000000</pre>
                    <h3>Useful events</h3>
                    <p><code>PostBeginPlay</code>, <code>Trigger</code>, <code>Timer</code>,
                    <code>Touch</code> and <code>Destroyed</code></p>
                    """;
        };
        return page("Context reference: " + escape(parent), """
                <p>Entries are inherited from the declared parent class. Confirm unusual members
                against exported engine classes or UCC.</p>
                """ + contextual + """
                <h3>Map event rule</h3>
                <p><code>TriggerEvent(Event, Self, EventInstigator)</code> activates every actor
                whose <code>Tag</code> matches this actor's <code>Event</code>.</p>
                """);
    }

    private static String page(String title, String body) {
        return "<html><body style='font-family:sans-serif;background:#11151b;color:#e8edf4;"
                + "padding:12px'><h2 style='margin-top:0'>" + title + "</h2>" + body
                + "</body></html>";
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
