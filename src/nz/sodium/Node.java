package nz.sodium;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.lang.ref.WeakReference;

class Node implements Comparable<Node> {

  final static Node NULL = new Node(Long.MAX_VALUE);

  Node(long rank) {
    this.rank = rank;
  }

  static class Target {

    Target(TransactionHandler<Unit> action, Node node) {
      this.action = new WeakReference<>(action);
      this.node = node;
    }

    final WeakReference<TransactionHandler<Unit>> action;
    final Node node;
  }

  long rank;
  List<Target> listeners = new ArrayList<>();

  /**
   * @return true if any changes were made.
   */
  boolean linkTo(TransactionHandler<Unit> action, Node target, Target[] outTarget) {
    var changed = target.ensureBiggerThan(rank, new HashSet<>());
    var t = new Target(action, target);
    listeners.add(t);
    outTarget[0] = t;
    return changed;
  }

  void unlinkTo(Target target) {
    listeners.remove(target);
  }

  boolean ensureBiggerThan(long limit, Set<Node> visited) {
    if (rank > limit || visited.contains(this)) {
      return false;
    }
    visited.add(this);
    rank = limit + 1;
    for (var l : listeners) {
      l.node.ensureBiggerThan(rank, visited);
    }
    visited.remove(this);
    return true;
  }

  @Override
  public int compareTo(Node o) {
    return (rank < o.rank) ? -1 : (rank > o.rank) ? 1 : 0;
  }

}
