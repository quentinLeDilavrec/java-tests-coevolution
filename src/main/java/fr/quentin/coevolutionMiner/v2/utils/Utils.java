package fr.quentin.coevolutionMiner.v2.utils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;

import fr.quentin.coevolutionMiner.v2.evolution.Evolutions;
import fr.quentin.impactMiner.Evolution;
import fr.quentin.impactMiner.ImpactAnalysis;
import fr.quentin.impactMiner.ImpactElement;
import fr.quentin.impactMiner.Position;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;

public class Utils {

	public static List<String> getCommitIdBeforeAndAfter(ImpactElement rootCause) {
		String commitIdBefore = null;
		String commitIdAfter = null;
		for (Entry<Object, Position> entry : rootCause.getEvolutionWithNonCorrectedPosition().entrySet()) {
			Evolutions.Evolution evo;
			if (entry.getKey() instanceof Evolutions.Evolution.DescRange) {
				evo = ((Evolutions.Evolution.DescRange) entry.getKey()).getSource();
			} else {
				continue;
			}
			if (commitIdBefore == null) {
				commitIdBefore = evo.getCommitBefore().getId();
			} else if (!commitIdBefore.equals(evo.getCommitBefore().getId())) {
				throw new RuntimeException("wrong commitIdBefore");
			}
			if (commitIdAfter == null) {
				commitIdAfter = evo.getCommitAfter().getId();
			} else if (!commitIdAfter.equals(evo.getCommitAfter().getId())) {
				throw new RuntimeException("wrong commitIdAfter");
			}
		}
		return Arrays.asList(commitIdBefore, commitIdAfter);
	}

	public static Position temporaryFix(Position position, Path rootDir) {
		try {
			return new Position(rootDir.relativize(Paths.get(position.getFilePath())).toString(), position.getStart(),
					position.getEnd() + 1);
		} catch (Exception e) {
			return new Position(position.getFilePath(), position.getStart(), position.getEnd() + 1);
		}
	}

	public static boolean isTest(CtElement e) {
		if (e == null) {
			return false;
		} else if (e instanceof CtExecutable) {
			return ImpactAnalysis.isTest((CtExecutable) e);
		} else {
			return false;
		}
	}

	public static boolean isParentTest(CtElement e) {
		if (e == null) {
			return false;
		}
		CtMethod p = e.getParent(CtMethod.class);
		if (p == null) {
			return false;
		}
		return ImpactAnalysis.isTest(p);
	}

}