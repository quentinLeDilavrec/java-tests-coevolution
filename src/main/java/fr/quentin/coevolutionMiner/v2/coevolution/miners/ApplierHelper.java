package fr.quentin.coevolutionMiner.v2.coevolution.miners;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;

import com.github.gumtreediff.actions.model.Action;
import com.github.gumtreediff.actions.model.Delete;
import com.github.gumtreediff.actions.model.Insert;
import com.github.gumtreediff.actions.model.Move;
import com.github.gumtreediff.actions.model.Update;
import com.github.gumtreediff.tree.ITree;

import gumtree.spoon.apply.AAction;
import gumtree.spoon.apply.ActionApplier;
import gumtree.spoon.apply.WrongAstContextException;
import gumtree.spoon.builder.SpoonGumTreeBuilder;
import gumtree.spoon.diff.Diff;
import gumtree.spoon.diff.DiffImpl;
import gumtree.spoon.diff.MultiDiffImpl;
import spoon.compiler.Environment;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.support.StandardEnvironment;

public class ApplierHelper {
    
    public static CtElement applyEvolutions(SpoonGumTreeBuilder scanner, ITree middle, Diff diff)
            {
        List<Action> retryList = new ArrayList<>();
        for (Action action : ((DiffImpl)diff).getActionsList()) {
                try {
                        auxApply(scanner, middle, action);
                        
                } catch (gumtree.spoon.apply.WrongAstContextException e) {
                        retryList.add(action);
                }
        }
        for (Action action : retryList) {
            try {
                    auxApply(scanner, middle, action);
            } catch (gumtree.spoon.apply.WrongAstContextException e) {
                    throw new RuntimeException(e);
            }
        }

        CtElement middleE = null;
        Queue<ITree> tmp = new LinkedBlockingDeque<>();
        tmp.add(middle);

        while (middleE == null) {
                ITree curr = tmp.poll();
                middleE = (CtElement) curr.getMetadata(SpoonGumTreeBuilder.SPOON_OBJECT);
                List<ITree> children = curr.getChildren();
                tmp.addAll(children);
        }
        return middleE;
    }

    public static void auxApply(final SpoonGumTreeBuilder scanner, ITree middle, Action action)
                    throws WrongAstContextException {
            if (action instanceof Insert) {
                    ActionApplier.applyAInsert((Factory) middle.getMetadata("Factory"),
                                    scanner.getTreeContext(), (Insert & AAction<Insert>) action);
            } else if (action instanceof Delete) {
                    ActionApplier.applyADelete((Factory) middle.getMetadata("Factory"),
                                    scanner.getTreeContext(), (Delete & AAction<Delete>) action);
            } else if (action instanceof Update) {
                    ActionApplier.applyAUpdate((Factory) middle.getMetadata("Factory"),
                                    scanner.getTreeContext(), (Update & AAction<Update>) action);
            } else if (action instanceof Move) {
                    ActionApplier.applyAMove((Factory) middle.getMetadata("Factory"),
                                    scanner.getTreeContext(), (Move & AAction<Move>) action);
            } else {
                    throw null;
            }
    }

}
