#!/bin/bash
grep -e MatchEngineStarter -e Controller -e WarmStart -e ModeControlMessage -e SnapLoader:Replay -e Switching -e Moving -e Snapshot -e TRACK logs/output.log | grep -v HealthMonitor | grep -v PersistThread
