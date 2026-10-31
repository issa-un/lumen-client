package app;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapt's callouts: 30 per situation, so the same one rarely shows twice in a fight. Each
 * block is one situation, one callout per line. Opponents are "they", since Adapt cannot know
 * who is behind the screen.
 */
final class AdaptCallouts {
    private static final Map<String, String[]> LINES = new HashMap<>();
    private static final String[] NONE = {};

    private AdaptCallouts() {}

    static String[] lines(String situation) {
        return LINES.getOrDefault(situation, NONE);
    }

    private static void add(String situation, String block) {
        LINES.put(situation, block.strip().lines().map(String::strip).filter(line -> !line.isEmpty())
                .toArray(String[]::new));
    }

    static int total() {
        return LINES.values().stream().mapToInt(lines -> lines.length).sum();
    }

    static {
        add("RUN", """
                RUN. Book it.
                Get out. Now.
                You're cooked. Disengage.
                Leave. Heal. Come back.
                Pearl out, this fight is gone.
                Back off before you drop.
                Not your fight. Run.
                Wind charge away and reset.
                Live first, win later. Run.
                Out of there, you're one hit.
                Retreat. Seriously.
                Break line of sight and heal.
                You lose this trade. Leave.
                Get distance. Eat. Return.
                Bail. You can't out-trade this.
                Disengage and regroup.
                Run now, revenge later.
                One more hit ends you. Go.
                Pillar up or pearl out.
                Turn around and sprint.
                Don't be a hero. Run.
                Escape first. Think after.
                Get away from them. Now.
                This is a loss. Cut it.
                Fly, jump, pearl. Just leave.
                Save the totem. Run.
                Too low to fight. Leave.
                They'll finish you. Book it.
                Gone. Go. Get out.
                Reset the fight. Run away.
                """);
        add("NO_TOTEMS_YOU", """
                No totems left. Play safe.
                Out of totems. Don't take risks.
                Last life. Every hit counts.
                No backup. Trade carefully.
                Zero totems. Keep your distance.
                One life left. Don't get greedy.
                No totems. Heal before engaging.
                Totems gone. Fight smart.
                No second chance. Watch the slams.
                You're on your real heart bar now.
                Out of pops. Respect their damage.
                No totems. Bait, don't brawl.
                Careful. Next lethal is real.
                No safety net. Space them out.
                Totemless. Avoid their mace drops.
                Play it slow, no totems left.
                Don't trade evenly with no totems.
                Your next death is final. Be careful.
                No totems: shield their slams.
                Last stand. Make it clean.
                No totems, so eat early.
                Hold your ground, don't overextend.
                Without totems, a slam kills. Move.
                Every trade matters now.
                No pops left. Stay out of slam range.
                Look for the exit, no totems.
                Patience. You're on your final life.
                No totems. Fight near cover.
                Play defense, no totems.
                Don't chase. No totems.
                """);
        add("LAST_TOTEM_YOU", """
                That was your last totem.
                Last pop. Get out or end it.
                Final totem gone. Careful now.
                Last totem used. Heal up.
                No more pops after that one.
                That was the last one. Play safe.
                Last totem burned. Reset.
                Final pop. Don't take another slam.
                Out of totems now. Back off.
                Last totem gone, eat a gapple.
                That totem was your last life.
                Final pop. Move, don't stand still.
                Last totem. Respect their mace.
                No more totems. Decide: run or finish.
                That was it. No more totems.
                Last pop gone. Shield up.
                You burned your last totem.
                Final totem. Get distance.
                Last one used. Don't get greedy.
                Out of pops. Heal before round two.
                Last totem down. Play the long game.
                No more totems. Watch their height.
                That pop was your last.
                Final totem. Pearl out if it's close.
                Last life now. Make it count.
                Totems empty. Keep moving.
                Last pop gone. Bait their slam.
                No totems after this. Be careful.
                That was the final one.
                Last totem. Breathe and reset.
                """);
        add("YOU_POPPED", """
                Totem popped. Get away and reset.
                You popped. Heal up.
                Pop! Don't take the follow-up.
                Totem gone. They'll go for the kill.
                Popped. Move, they'll slam again.
                You popped. Shield their next drop.
                Totem used. Get distance now.
                Popped. Eat a gapple.
                That was lethal. Respect it.
                You popped. Don't stand still.
                Totem down. They'll chase. Be ready.
                Popped. Pearl away to reset.
                You popped. Swap a new totem in.
                That slam would've killed you.
                Popped. They're about to send it.
                Totem gone. Play the next 5 seconds safe.
                You popped. Break their combo.
                Pop! Jump out of their range.
                Totem used. Don't trade right now.
                Popped. They'll go up for another slam.
                You popped. Look up, they're coming.
                Totem gone. Heal before you fight.
                Popped. Wind charge out.
                That hit was lethal. Adapt.
                You popped. Get under cover.
                Totem burned. Slow the fight down.
                Popped. Watch for the follow-up slam.
                You popped. Stay off the ground.
                Pop. Breathe. Reset.
                You popped. Counter their next drop.
                """);
        add("YOU_LOW", """
                You're low. Heal now.
                Low health. Eat.
                Careful, you're low.
                Low hp. Get some distance.
                Heal before your next trade.
                You're low. Don't take a slam.
                Low. Shield up.
                Health is low. Play safe.
                Eat a gapple, you're low.
                Low hp, avoid the trade.
                You're low. Back up a few blocks.
                Low health. Watch their mace.
                Heal up, they smell blood.
                You're low. Stay airborne.
                Low. Break their line of sight.
                Careful, one slam ends this.
                Low hp. Totem in offhand?
                You're low. Use your wind charge.
                Low. Make them miss.
                Heal while you can.
                You're low. Don't chase them.
                Low health. Kite them.
                Low. Take the high ground.
                You're one combo from dead.
                Low hp. Pearl if you must.
                You're low. Reset the fight.
                Low. Make space.
                Health check: low. Heal.
                You're low. Don't let them go up.
                Low. Be patient.
                """);
        add("COMBOED", """
                You're getting combo'd. Reset.
                Break the combo. Jump out.
                They're chaining hits. Get away.
                Combo'd. Wind charge out.
                Stop eating hits. Move.
                You're stuck in their combo.
                Break it: pearl or jump.
                Three hits in a row. Change something.
                They're juggling you. Get air.
                Combo'd. Strafe out of it.
                Get out of their rhythm.
                Stop standing still, you're getting combo'd.
                Combo incoming. Break line.
                They've got your timing. Switch it up.
                You're getting farmed. Reset.
                Combo'd. Shield to stop it.
                They're on you. Create space.
                Get off the ground, they're comboing.
                Combo'd again. Change your angle.
                Their combo works. Don't let it start.
                Jump reset, you're getting combo'd.
                Break the chain. Now.
                You're eating a full combo. Leave.
                Counter-hit instead of running straight.
                They're winning the trades. Reset.
                Stuck in a combo. Wind charge.
                Get out of reach, then come back.
                Combo'd. Don't panic, reposition.
                Hit back or get out, not both.
                Combo'd. Change direction.
                """);
        add("THEY_SLAM", """
                They slammed from height. Get under them.
                They go up to slam. Look up.
                Slam! Watch the sky.
                They love height. Shield the drop.
                That was a slam. Don't stand still.
                They dropped on you. Move sideways.
                Slam incoming next time? Shield.
                They come from above. Track them.
                Big drop. Strafe out of the landing.
                They slam. Hit them on the way down.
                Watch their wind charge, it means a slam.
                They went up. Expect another drop.
                Slammed. Stay off their landing spot.
                Height is their thing. Take it away.
                They slam. Get your own height.
                Shield up when they're above you.
                That slam hurt. Don't let it repeat.
                They fall on you. Dodge late.
                Slam. Go up too, meet them in the air.
                They drop from above. Watch the shadow.
                Slam came in. Axe their shield after.
                They're vertical. Fight vertical.
                Slammed. Next time move as they fall.
                They go high. Punish when they land.
                Slam. Don't trade under them.
                They're a slammer. Stay mobile.
                That was a height slam. Look up more.
                They slam. Bait it, then dodge.
                Dropped on. Counter in the air.
                Slam. Keep your head up.
                """);
        add("THEY_SLAM_HABIT", """
                They always slam. Shield every drop.
                Slammer. Look up and shield.
                Third slam. They only know one trick.
                They always go up. Bait the slam.
                Predictable slams. Dodge on the drop.
                They're a one-trick slammer. Punish it.
                Every fight they go up. Be ready.
                Slam spam. Shield it and axe them.
                They live in the sky. Shield up.
                They slam every time. Stay mobile.
                Same slam again. Move at the last second.
                They rely on slams. Take away their height.
                They always drop on you. Strafe late.
                Their whole game is the slam. Counter it.
                Slam habit spotted. Shield, then hit back.
                They go up every time. Wait for the drop.
                Slam slam slam. Shield shield shield.
                They need height. Fight under a roof.
                Predictable. Shield their drop, punish the landing.
                They slam on repeat. Wind charge them mid-air.
                You've seen their slam. Now dodge it.
                Slam number three. They won't change. You do.
                They chase the slam. Make them miss.
                They're slam-dependent. Deny the height.
                Watch the wind charge, slam follows.
                Their slams hit every time. Look up.
                Slam pattern: up, wait, drop. Dodge on drop.
                Shield is your best friend vs this one.
                They always drop. Meet them in the air.
                Adapted: they always slam. Shield it.
                """);
        add("THEY_HIGH", """
                They're going up. Get ready.
                Look up, they're climbing.
                They're high. Shield incoming drop.
                They went up. Slam coming.
                Height! Watch above you.
                They're above you. Move.
                They took the sky. Don't stand still.
                Look up!
                They're climbing. Bait the drop.
                Incoming from above.
                They're up high. Track them.
                They're setting up a slam.
                Up there! Shield ready.
                They're flying up. Strafe.
                They got height. Get out from under.
                They're high. Go up with them.
                Above you. Get ready to dodge.
                They're rising. Something's coming.
                Watch the sky.
                They're preparing a drop.
                They gained height. Be careful.
                High up. Don't stay in one spot.
                They're above. Wait for the fall.
                Climbing fast. Shield up.
                They launched. Look up.
                Sky threat. Move.
                They're way up. Slam incoming.
                They're up. Punish the landing.
                Above you, falling soon.
                Height spotted. Stay mobile.
                """);
        add("YOU_HIGH", """
                You're above them. Slam!
                You've got height. Send it.
                You're up high. Drop on them.
                Height advantage. Mace out.
                Big fall. Go for the slam.
                You're above. Make it count.
                Slam time.
                Drop on them now.
                You've got the sky. Use it.
                Fall onto them. Mace ready.
                Height is yours. Slam.
                You're falling. Aim the slam.
                Above them. Don't miss.
                Perfect height. Drop.
                Send the slam.
                You're up. Land on their head.
                Big drop available. Take it.
                You're high. Hit them on the way down.
                Mace slam incoming. Line it up.
                They're under you. Slam.
                Fall is big. Big hit coming.
                Land it.
                Up high. Crush them.
                Height advantage, use it now.
                Line up the drop.
                Slam them before they move.
                You've got the fall. Go.
                Drop. Smash. Win.
                Above them, mace in hand. Go.
                Big slam window. Don't waste it.
                """);
        add("THEY_MACE", """
                They hit with a mace. Watch their height.
                Mace user. Don't let them go up.
                They use a mace. Shield the drops.
                Mace in hand. Keep them grounded.
                Mace means slams. Look up.
                They're on mace. Fight in the air.
                Mace player. Take away their fall.
                Watch for wind charges, mace follows.
                Their mace only hurts from height.
                Mace user. Stay out from under them.
                Keep them on the ground, their mace is weak there.
                Mace damage is height. Deny it.
                They mace. You shield.
                Mace swing. Strafe their landing.
                Mace player. Fight under cover.
                Watch their fall, not their hand.
                Mace user. Bait the slam.
                Mace means they go up. Follow them.
                On the ground their mace does little.
                Mace in their hand. Stay mobile.
                They carry a mace. Expect drops.
                Mace player. Punish after each slam.
                Their mace needs a fall. Stop it.
                Mace user. Don't stand still.
                Mace means big hits. Shield ready.
                Mace user. Hit them while they're grounded.
                Mace swings are slow on the ground. Trade.
                Mace player. Watch the wind charges.
                Mace equipped. Keep them low.
                Their damage comes from falling. Remember that.
                """);
        add("THEY_SPEAR", """
                Spear user. They hit from far away.
                Spear reach. Get closer.
                They poke from range. Close the gap.
                Spear in hand. Don't stay at mid range.
                Spear lunge incoming. Strafe.
                They have reach. Go in close.
                Spear user. Fight inside their range.
                They lunge. Dodge sideways.
                Spear means distance. Take it away.
                They're poking. Rush them.
                Spear reach is long. Don't hover.
                They dash with the spear. Watch it.
                Close range beats the spear.
                Spear user. Bait the lunge.
                They hit from 4 blocks. Stay closer.
                They spear-dash in. Shield.
                Spear player. Punish after the lunge.
                Get in their face, the spear needs space.
                They keep distance. Go to them.
                Spear lunge just hit. Reset spacing.
                They outrange you. Cut the gap.
                Spear user. Strafe, don't back up.
                Backing up helps their spear. Go in.
                They jab from range. Get airborne.
                Spear reach. Don't walk straight at them.
                Spear user. Hit them after their dash.
                They lunge forward. Sidestep.
                Long reach weapon. Stay tight.
                Spear spotted. Change your spacing.
                They poke and run. Chase smart.
                """);
        add("THEY_SWORD", """
                Sword user. Watch their crits.
                Sword player. Keep the trades even.
                They're on sword. Use your height.
                Sword in hand. Jump reset their hits.
                Sword user. Don't get combo'd.
                They swing fast. Strafe.
                Sword. Out-space them.
                Sword player. Go air, they're ground.
                They crit you. Stop jumping into them.
                Sword user. Mace from above wins.
                Sword trades. Watch your health.
                They're a sword player. Slam them.
                Sword means combos. Break them.
                Sword user. Shield their crits.
                Fast swings. Keep moving.
                Sword. Take the fight vertical.
                They're comboing with a sword. Reset.
                Sword player. Wind charge and slam.
                Sword user. Don't stand at their range.
                Their sword hits often. Heal early.
                Sword in hand. Bait the crit.
                They'll strafe. Track them.
                Sword user. Hit first.
                Sword player. Fight in the air.
                Watch their jump crits.
                Sword. Make them chase you.
                Sword user. Punish their whiffs.
                They're good on the ground. Go up.
                Sword. Your mace outdamages it from height.
                Sword user. Change the pace.
                """);
        add("THEY_AXE", """
                They have an axe. Drop your shield.
                Axe! Don't block, they'll break it.
                They're axing your shield. Stop blocking.
                Shield down, axe incoming.
                Axe user. Dodge instead of blocking.
                Your shield is breaking. Strafe.
                Axe in hand. Shield won't save you.
                They break shields. Move instead.
                Don't hold block vs an axe.
                Axe player. Fight without the shield.
                They're cracking your shield.
                Shield's getting disabled. Dodge.
                Axe swing. Unblock.
                Axe beats shield. Use movement.
                They axe, you strafe.
                Stop turtling, they have an axe.
                Your shield is a target. Lower it.
                Axe user. Trade hits instead.
                Axe means no blocking. Remember.
                They're shield breaking you.
                Axe in their hand. Change plan.
                Don't block now. They'll disable it.
                Axe swing coming. Jump back.
                They counter shields. Go aggressive.
                Axe user. Punish after their swing.
                Shield up gets you axed.
                They switched to axe. Stop blocking.
                Axe. Get out of block.
                Shield disable incoming. Move.
                Axe player. Fight in the open.
                """);
        add("THEY_SHIELD", """
                They turtle behind their shield. Axe them.
                Shield spam. Break it.
                They block a lot. Axe hit first.
                Turtle spotted. Shield break them.
                They hide behind the shield. Go around.
                Shield up again. Axe it.
                They block everything. Disable it.
                Turtling. Hit from behind.
                Shield player. Get an axe out.
                They block, you break.
                Stop hitting the shield. Axe it.
                They love blocking. Punish it.
                Shield user. Strafe to their side.
                Turtle. Slam over the shield? No, axe first.
                Their shield is up a lot. Break, then slam.
                Blocker. Make them drop the shield.
                Shield up. Don't waste your mace.
                They're defensive. Break the shield.
                Turtle mode. Axe, then mace.
                They block your slams. Disable first.
                Shield wall. Axe through.
                They hold block. Circle them.
                Shield spam detected. Axe.
                They're scared. Pressure them.
                Defensive player. Break the shield.
                Shield every hit. Go behind them.
                They block the front. Hit the back.
                Turtle. Bait the block, then axe.
                They live behind the shield. End that.
                Adapted: they block. Axe them.
                """);
        add("THEY_PEARLED", """
                They pearled. Watch where it lands.
                Pearl thrown. Chase it.
                They're pearling away. Follow.
                Pearl! Get to the landing spot.
                They escaped with a pearl.
                Pearl out. Cut them off.
                They pearled. Predict the landing.
                Pearl thrown. Be there when it lands.
                They're running with a pearl.
                Pearl! Track it.
                They pearled. Pearl after them.
                They teleported. Turn around.
                Pearl landing. Get ready.
                They used a pearl. They're scared.
                Pearl! Don't let them reset.
                They're pearling to heal. Chase.
                Pearl thrown. Hit them on arrival.
                They pearled behind you. Turn.
                They're repositioning. Follow the pearl.
                Pearl gone. They wanted out.
                They escaped. Push the advantage.
                Pearl spotted. Intercept.
                They're bailing. Don't let them.
                Pearl! Meet them there.
                They're using pearls to dodge.
                They pearled up. Slam incoming?
                Pearl away. Heal while they travel.
                They teleport. You chase.
                Pearl thrown. Watch the arc.
                They left. Follow or heal.
                """);
        add("THEY_PEARL_HABIT", """
                They pearl away when low. Cut them off.
                Low, then pearl. Every time.
                They run with pearls when hurt. Chase.
                Pearl habit. Be ready when they're low.
                They always pearl out. Follow it.
                They escape when low. Finish them first.
                Pearl runner. Keep pressure on.
                They pearl at low hp. Predict it.
                Same escape again. Intercept.
                They'll pearl soon. Get close.
                They pearl away to heal. Don't let them eat.
                Pearl when low, every fight. Chase it.
                Escape artist. Stay on them.
                They're about to pearl. Stick to them.
                They pearl to reset. Deny it.
                Pearl habit spotted. Follow the throw.
                They run at low health. Push now.
                They always bail. End it fast.
                Pearl incoming, they're low.
                Low and pearling. Classic. Chase.
                They pearl out every time. Be there.
                Their escape is the pearl. Cut it.
                They run when it's close. Close it.
                Pearl when low. You know the move.
                Predictable escape. Pearl after them.
                They'll pearl now. Watch for it.
                They don't finish fights, they leave. Chase.
                Low hp means pearl. Get ready.
                Adapted: they pearl when low.
                They run. You hunt.
                """);
        add("THEY_WINDCHARGE", """
                They spam wind charges. Watch for the slam.
                Wind charge user. They're going up.
                They wind charge a lot. Bait it.
                Wind charge, then slam. Shield.
                They use wind charges to fly. Track them.
                Wind charge spam. Expect drops.
                They launch with wind charges. Look up.
                They escape with wind charges.
                Wind charge thrown. Height incoming.
                They're mobile. Stay on them.
                Wind charges everywhere. Stay calm.
                They boost a lot. Hit them mid-air.
                Wind charge pattern. Predict the drop.
                They fly around. Fight in the air.
                Wind charge user. Slam them back.
                They wind charge to dodge. Wait it out.
                They go up with wind charges. Follow.
                Wind charge means slam soon.
                They're boosting. Get your own height.
                Wind charges thrown. Watch above.
                They rely on wind charges. Make them run out.
                Mobile player. Keep pressure.
                Wind charge spam. Shield ready.
                They boost away. Chase or heal.
                They fly in, they fly out. Time it.
                Wind charge user. Punish the landing.
                They launch. You shield.
                Wind charge again. Look up.
                They're airborne a lot. Air fight.
                Adapted: wind charge means slam.
                """);
        add("THEY_POPPED", """
                They popped! Send it.
                Totem gone on them. Push now.
                They popped. Go for the kill.
                Pop! Keep the pressure.
                They burned a totem. Don't let them heal.
                They popped. Slam again.
                Totem pop. They're on 1 heart plus.
                They popped. Finish it.
                Pop! They're weak right now.
                They used a totem. Stay on them.
                They popped. Second slam now.
                Totem down. Chase them.
                They popped. Don't let them pearl.
                Pop! Go go go.
                They popped. Hit them while they're stunned.
                Totem gone. They'll try to heal. Stop it.
                They popped. Follow up fast.
                That was lethal on them. Again.
                Pop! Big damage window.
                They popped. Keep slamming.
                Totem popped. They're scared now.
                They popped. Cut off the escape.
                Pop. Pressure, pressure.
                They burned one. Make them burn another.
                They popped. Mace up.
                Pop! Don't back off now.
                They used a totem. Keep it going.
                Totem gone. One more.
                They popped. Stay aggressive.
                Pop! You're winning this.
                """);
        add("THEY_POPPED_MANY", """
                Another pop! They're running out.
                They popped again. Keep going.
                Multiple pops. They can't have many left.
                Pop number two. Push harder.
                They're burning totems fast. Send it.
                Again! They're almost out.
                Totems are draining. Don't stop.
                They popped again. Finish them.
                Pop after pop. You're winning.
                They're out of answers. Keep slamming.
                Another totem gone. End it.
                They keep popping. Keep hitting.
                Totem count dropping. Push.
                Pop! They're running on fumes.
                They've popped a lot. One more push.
                Again. They're desperate now.
                Totems going fast. Stay on them.
                They're nearly empty. Go.
                Another pop. Don't let them reset.
                They popped again. They'll run soon.
                Keep the pops coming.
                They're out soon. Pressure.
                Pop again! They're cooked.
                Their totems won't last. Keep going.
                Another one. Chase if they pearl.
                They popped a bunch. Finish it now.
                Pop. Pop. Win.
                They're spending totems like crazy.
                Almost out. Send everything.
                Another pop! Close it out.
                """);
        add("THEY_LOW", """
                They're low. Finish it.
                Low health on them. Send it.
                They're almost dead. Push.
                They're low. Don't let them heal.
                Kill window. Go.
                They're one combo from dead.
                Low. Slam them.
                They're weak. Pressure.
                Finish them before they eat.
                They're low. Cut off the pearl.
                Low health. Go for the kill.
                They're on their last hearts.
                Send it, they're low.
                They're low. Stay on them.
                Almost done. One more hit.
                Low hp. Don't back off.
                They're dying. Finish.
                Low. They'll try to run.
                They're low. Mace them.
                Now or never. They're low.
                They're low. Watch for the totem.
                Kill them now.
                They're barely standing.
                Low. Push them.
                They're low. Close the gap.
                Finish line. Go.
                They can't take much more.
                They're low. Keep it clean.
                Low on them. Big hit ends it.
                They're low. Be the last hit.
                """);
        add("THEY_EATING", """
                They're eating. Hit them now.
                Gapple! Interrupt it.
                They're healing. Punish.
                They're eating. Free hits.
                Eating a gapple. Don't let them finish.
                They stopped to eat. Attack.
                Gapple time for them. Pressure.
                They're healing. Slam them.
                They're eating. Close the gap.
                Gapple. Hit them out of it.
                They're eating, they're not blocking.
                Free damage while they eat.
                They're healing up. Stop it.
                Eating! Go in.
                They're eating. Mace from above.
                Gapple spotted. Punish it.
                They paused to heal. Push.
                They're eating, so they can't hit.
                Heal attempt. Deny it.
                They're eating. Wind charge and slam.
                They're on a gapple. Pressure them.
                Eating means vulnerable.
                They're healing. Don't give them time.
                Gapple. Hit hard.
                They eat, you hit.
                They're eating. Chase if they run.
                Eating! Free window.
                They're healing. Make them pay.
                Interrupt the gapple.
                They're eating. Your turn.
                """);
        add("THEY_RUNNING", """
                They're running. Chase.
                They're backing off. Follow.
                They're leaving. Don't let them heal.
                Running away. Cut them off.
                They're retreating. Push.
                They want out. Stay on them.
                They're fleeing. Pearl after them.
                They're running low. Finish it.
                They're escaping. Chase smart.
                Running. Wind charge after them.
                They're backing up. Close the gap.
                They're scared. Push.
                They're retreating to heal. Stop it.
                They ran. Follow or heal yourself.
                They're getting away. Decide now.
                They're running. Don't fall for a trap.
                Retreat spotted. Chase.
                They're running. Throw the pearl.
                They're leaving the fight. Push.
                They're backing off. Take their space.
                Running. They're low.
                They're kiting you. Be careful.
                They're running. Sprint after them.
                They broke off. Follow up.
                They retreat. You advance.
                Running away. Chase or reset.
                They're scared of you. Use it.
                They're leaving. Don't let them eat.
                They're running. Go go go.
                They fled. Heal or hunt.
                """);
        add("THEY_GROUND", """
                They fight on the ground. Go air.
                Ground fighter. Get height.
                They stay low. Slam them.
                They don't go up. Use that.
                Ground player. Take the sky.
                They're grounded. Mace from above.
                They fight flat. Fight vertical.
                Ground fighter. Wind charge up.
                They don't use height. You should.
                They stay on the ground. Drop on them.
                Ground player. Height wins.
                They're grounded. Go up and slam.
                They fight on the floor. Take the air.
                No height from them. Abuse it.
                Ground fighter. Mace slam time.
                They stay low. Get above.
                They don't fly. You do.
                Ground player. Don't brawl with them.
                They're a ground fighter. Air beats that.
                They never go up. Slam.
                Ground game. Change to air game.
                They're flat. Go high.
                They don't use wind charges. You should.
                Ground fighter. Take the height and drop.
                They fight low. Hit high.
                They're stuck on the ground. Go up.
                Ground player. Slam them.
                They stay down. You go up.
                No air game from them. Abuse it.
                Adapted: they fight on the ground. Go air.
                """);
        add("LOSING", """
                You're losing this. Change something.
                They're ahead. Reset.
                Losing trades. Switch it up.
                They're winning. Back off and heal.
                You're behind. Play safer.
                Down on health. Fix that.
                They're out-trading you. Change angle.
                Losing. Try going air.
                They're ahead. Bait their move.
                You're behind. Heal first.
                Losing the fight. Disengage?
                They're winning trades. Use height.
                Behind. Don't force it.
                You're losing. Make them miss.
                They're ahead. Slow it down.
                Losing. Watch their pattern.
                Down bad. Reset the fight.
                They're winning. Shield their big hits.
                Behind on health. Eat.
                Losing. Try a different weapon.
                They have the upper hand. Take it back.
                You're behind. Go for one big slam.
                Losing trades. Stop trading.
                They're ahead. Space them out.
                Behind. Don't chase.
                Losing. Look for a pearl out.
                They're winning. Pressure their healing.
                Behind. Fight near cover.
                You're losing this round. Adapt.
                Down. Not out. Reset.
                """);
        add("WINNING", """
                You're winning. Keep it up.
                Ahead on health. Push.
                You're up. Stay aggressive.
                Winning trades. Keep going.
                You're ahead. Don't get greedy.
                Up on them. Finish it.
                You're winning. Watch their escape.
                Ahead. Keep pressure.
                You're out-trading them. Nice.
                Winning. Don't let them heal.
                You're up. Keep the rhythm.
                Ahead. Slam them again.
                Winning. Stay smart.
                You're ahead. Cut off the pearl.
                Up on health. Push now.
                You're winning. Don't overextend.
                Ahead. Keep them on the ground.
                Winning. One more big hit.
                You're up. They'll try something.
                Ahead. Watch for their totem.
                Winning. Stay on them.
                You're up. Close it out.
                Ahead. They're scared.
                Winning. Keep slamming.
                You've got this. Keep going.
                Ahead. Don't let them reset.
                You're winning. Play clean.
                Up. Finish them.
                Winning trades. Same again.
                You're ahead. End it.
                """);
        add("TRADING", """
                Even trades. Find an edge.
                Trading hits. Change the pace.
                It's even. Get height.
                Trading. Go for the slam.
                Even fight. Bait their move.
                You're trading. Heal first if needed.
                Even. Watch their pattern.
                Trading hits. Make them miss.
                It's close. Play smart.
                Trading. Try the air.
                Even exchange. Look for the opening.
                Trading. Shield their big hit.
                Even. Pressure them.
                Trading blows. Don't get combo'd.
                It's even. Who adapts first wins.
                Trading. Use your wind charge.
                Even fight. Stay patient.
                Trading. Get the first hit next time.
                Even. Watch for their pearl.
                Trading hits. Change your angle.
                It's even. Go vertical.
                Trading. Don't stand still.
                Even. Look for their weakness.
                Trading. Time your hits.
                It's a trade war. Break it.
                Even. Bait the slam.
                Trading. Watch your health.
                Even fight. Break their rhythm.
                Trading. Outsmart them.
                It's even. Adapt.
                """);
        add("CLOSE_FIGHT", """
                Close fight. Every hit matters.
                Neck and neck. Stay sharp.
                It's close. Don't make mistakes.
                Close. One slam decides it.
                Tight fight. Play clean.
                Dead even. Focus.
                Close one. Watch their next move.
                It's anyone's fight.
                Close. Heal first, then push.
                Even health. Get the first big hit.
                Close fight. Stay calm.
                Tight. Make them miss.
                It's close. Use your totem wisely.
                Even. Who lands the slam wins.
                Close. Don't panic.
                Tight fight. Take the height.
                Even. Be patient.
                Close. Trust your timing.
                Neck and neck. Breathe.
                It's tight. Don't chase.
                Close fight. Bait them.
                Even. One mistake ends it.
                Close. Stay aggressive but smart.
                Tight. Keep moving.
                Close one. Watch their pearl.
                Even health. Go air.
                Close. Make the play.
                Tight. Shield their drop.
                It's close. Finish strong.
                Even fight. Win the next exchange.
                """);
        add("BIG_HIT", """
                Big hit taken. Don't take another.
                Ouch. Move.
                That hurt. Reset.
                Big damage. Heal up.
                Heavy hit. Change position.
                Huge hit. Stay away from that.
                That one hurt. Watch for a repeat.
                Big hit. Shield up.
                Massive damage. Get out.
                That's a big chunk. Be careful.
                Heavy hit. Don't stand there.
                Big hit. They found an opening. Close it.
                Huge damage. Heal.
                That was a lot. Reset.
                Big hit. Move more.
                That one landed hard. Adapt.
                Big hit. Don't let it happen twice.
                Heavy. Back off a second.
                That hurt a lot. Watch their setup.
                Big damage taken. Play safe.
                Huge hit. Their timing is good.
                Ouch. Change your pattern.
                Big hit. Eat soon.
                That's a big hit. Respect it.
                Heavy damage. Strafe.
                Big hit. Look up next time.
                That landed. Fix your positioning.
                Big damage. Stay mobile.
                Huge hit. Make them miss the next one.
                Big hit. Breathe. Reset.
                """);
        add("CHIP", """
                Just chip damage. Keep going.
                Small hit. Stay focused.
                Chip. Don't worry.
                Light hit. Keep your plan.
                Little damage. Push.
                Chip damage. Ignore it.
                Small hit. Stay aggressive.
                Tiny hit. Keep pressure.
                Just a tap. Keep going.
                Chip. You're fine.
                Small damage. Stay calm.
                Light hit. Don't panic.
                Chip. They're poking.
                Small. Keep trading.
                Just chip. Find your opening.
                Little hit. Watch the big ones.
                Chip damage. Stay on them.
                Small hit. Don't back off.
                Chip. Keep your height.
                Tiny damage. Push forward.
                Small hit. They're weak on the ground.
                Just a scratch. Go.
                Chip. Their real damage is the slam.
                Small hit. Stay patient.
                Chip. Don't let it add up.
                Light damage. Keep the pressure.
                Small one. Focus.
                Chip damage. Eat later.
                Little hit. Keep moving.
                Chip. Nothing to worry about.
                """);
        add("ON_FIRE", """
                You're on fire. Water or run.
                Burning! Put it out.
                On fire. Get water.
                Fire damage. Move off it.
                You're burning. Find water.
                On fire. Back off.
                Burning. Don't fight while on fire.
                Fire! Get out.
                You're on fire. Heal.
                Burning. Water bucket.
                On fire. Stop trading.
                Fire. Get away from the flames.
                You're burning up. Fix it.
                On fire. Jump in water.
                Burning. Watch your health.
                Fire damage adding up. Move.
                You're on fire. Reset.
                Burning. Don't stand in it.
                On fire. Get to safety.
                Fire. Eat if you have to.
                You're burning. Back away.
                On fire. Careful.
                Burning! Water now.
                Fire's hurting you. Stop it.
                On fire. Don't chase.
                You're on fire. Play safe.
                Burning. Leave the fire.
                On fire. Get distance.
                Fire. Put it out first.
                You're burning. Fix it, then fight.
                """);
        add("FIGHT_START", """
                New opponent. Watch their style.
                Fight on. Learn their pattern.
                New fight. Stay sharp.
                Here we go. Watch what they do.
                Fight started. Play it safe first.
                New player. Scout their moves.
                Fight! Get height early.
                New opponent. Don't rush in.
                Let's go. Watch their weapon.
                Fight on. First hit matters.
                New fight. Stay calm.
                Opponent spotted. Read them.
                Fight time. Watch for slams.
                New player. Learn, then punish.
                Here they come. Be ready.
                Fight started. Test their shield.
                New opponent. Bait their move.
                Fight on. Stay mobile.
                New fight. Watch their pearls.
                Let's see what they've got.
                Fight! Keep your totem ready.
                New player. Who adapts faster?
                Fight on. Patience first.
                New opponent. Get the high ground.
                Fight started. Watch their wind charges.
                Here we go. Focus.
                New fight. Start strong.
                Opponent locked. Read their play.
                Fight on. Trust your plan.
                New player. Adapting...
                """);
        add("ADAPTED", """
                Adapted. You know their style now.
                The wheel turns. You're learning them.
                Adapted to their play.
                You've seen enough. Counter them.
                Adapted. Their tricks won't work twice.
                The wheel turned. Change your plan.
                Adapted. Punish their habits.
                You know their pattern now.
                Adapted. Stay ahead of them.
                The wheel clicks. You're ready.
                Adapted to their timing.
                You've figured them out.
                Adapted. Now use it.
                The wheel turns again.
                Adapted. Their damage won't land the same way.
                Pattern learned. Exploit it.
                Adapted. Predict their next move.
                The wheel has turned. Strike back.
                You've adapted. Take control.
                Adapted to their style. Push.
                The wheel turns. Counter time.
                Adapted. You've seen it before.
                Learned their moves. Shut them down.
                Adapted. They're predictable now.
                The wheel turned. Your turn.
                Adapted. Time to win this.
                Fully adapted. End it.
                Adapted. They can't surprise you.
                The wheel spins. You evolve.
                Adapted.
                """);
        add("NEUTRAL", """
                Took a hit. Stay focused.
                Hit taken. Keep moving.
                Damage. Watch your health.
                Hit. Reset your position.
                Took one. Stay sharp.
                Hit taken. Don't panic.
                Damage taken. Find the source.
                Hit. Look around.
                Took damage. Stay mobile.
                Hit. Check your surroundings.
                Damage. Heal if needed.
                Hit taken. Be careful.
                Took a hit. Who was that?
                Hit. Stay alert.
                Damage. Move.
                Hit taken. Keep your guard up.
                Took one. Watch your back.
                Hit. Don't stand still.
                Damage. Stay calm.
                Hit taken. Reposition.
                Took damage. Keep going.
                Hit. Focus up.
                Damage taken. Watch for more.
                Hit. Stay on your toes.
                Took a hit. Adapt.
                Hit taken. Look up.
                Damage. Be ready.
                Hit. Keep your totem ready.
                Took one. Stay sharp.
                Hit taken. Keep fighting.
                """);
    }
}
