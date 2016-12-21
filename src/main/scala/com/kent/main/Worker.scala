package com.kent.main

import akka.actor.Actor
import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import akka.actor.Props
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.ClusterEvent.UnreachableMember
import akka.cluster.ClusterEvent.MemberRemoved
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.ClusterEvent.MemberEvent
import akka.cluster.Member
import akka.actor.RootActorPath
import akka.actor.ActorPath
import akka.actor.ActorRef
import com.kent.workflow.node.ActionNodeInstance
import com.kent.workflow.ActionActor
import com.kent.pub.ShareData
import com.kent.db.LogRecorder
import com.kent.workflow.WorkflowActor.Start

class Worker extends ClusterRole {
  val i = 0
  import com.kent.main.Worker._
  def receive: Actor.Receive = {
    case MemberUp(member) => register(member, getMasterPath)
    case UnreachableMember(member) =>
      log.info("Member detected as Unreachable: {}", member)
    case MemberRemoved(member, previousStatus) =>
      log.info("Member is Removed: {} after {}", member.address, previousStatus)
    case state: CurrentClusterState =>
    
    case CreateAction(ani) => sender ! createActionActor(ani)
    case _:MemberEvent => // ignore 
    
    case Start() => this.start()
  }
  /**
   * 创建action actor
   */
  def createActionActor(actionNodeInstance: ActionNodeInstance):ActorRef = {
		val actionActorRef = context.actorOf(Props(ActionActor(actionNodeInstance)) , 
		    actionNodeInstance.name)
		actionActorRef
  }
  /**
   * 获取master的路径
   */
  def getMasterPath(member: Member):Option[ActorPath] = {
    if(member.hasRole("master")){
    	Some(RootActorPath(member.address) /"user" / "master")    
    }else{
      None
    }
  }
  
  def start(): Boolean = {
    import com.kent.pub.ShareData._
     //日志记录器配置
     val logRecordConfig = (config.getString("workflow.log-mysql.user"),
                      config.getString("workflow.log-mysql.password"),
                      config.getString("workflow.log-mysql.jdbc-url"),
                      config.getBoolean("workflow.log-mysql.is-enabled")
                    )
      //创建日志记录器
      ShareData.logRecorder = context.actorOf(Props(LogRecorder(logRecordConfig._3,logRecordConfig._1,logRecordConfig._2,logRecordConfig._4)),"log-recorder")
      true
  }
}

object Worker extends App {
  case class CreateAction(ani: ActionNodeInstance)
  
  Seq("2851","2852").foreach {
    port =>
      val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port)
        .withFallback(ConfigFactory.parseString("akka.cluster.roles = [worker]"))
        .withFallback(ConfigFactory.load())
      ShareData.config = config
      val system = ActorSystem("workflow-system", config)
      val worker = system.actorOf(Worker.props, name = "worker")
      worker ! Start()
  }
  
  def props = Props[Worker]
  
}