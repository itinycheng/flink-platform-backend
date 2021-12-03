package com.flink.platform.dao.entity;

import com.flink.platform.common.graph.DAG;
import com.flink.platform.common.model.JobEdge;
import com.flink.platform.common.model.JobVertex;

/** Job flow dag. */
public class JobFlowDag extends DAG<Long, JobVertex, JobEdge> {}
