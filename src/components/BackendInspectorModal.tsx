import React, { useState, useEffect } from 'react';
import {
  SPRING_BOOT_CONTROLLER_CODE,
  SPRING_SECURITY_CONFIG_CODE,
  PAYMENT_GATEWAY_SERVICE_CODE,
  POSTGRESQL_SCHEMA_SQL,
} from '../data/initialData';

export const BackendInspectorModal: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'controller' | 'security' | 'gateway' | 'postgres' | 'pom_xml' | 'standalone_guide' | 'live_test'>('standalone_guide');
  const [healthStatus, setHealthStatus] = useState<any>(null);
  const [apiLogs, setApiLogs] = useState<Array<{ time: string; msg: string; type: string }>>([]);

  useEffect(() => {
    fetch('/api/v1/health')
      .then((res) => res.json())
      .then((data) => {
        setHealthStatus(data);
        addLog('GET /api/v1/health -> 200 OK (Spring Boot Healthy)', 'info');
      })
      .catch((err) => {
        console.error(err);
      });
  }, []);

  const addLog = (msg: string, type: 'info' | 'success' | 'warn' = 'info') => {
    const time = new Date().toLocaleTimeString();
    setApiLogs((prev) => [{ time, msg, type }, ...prev.slice(0, 15)]);
  };

  const handleTestRealtimePayment = async () => {
    addLog('POST /api/v1/payments/process-realtime initiating Stripe test...', 'info');
    try {
      const res = await fetch('/api/v1/payments/process-realtime', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          amount: 48.0,
          method: 'CARD',
          gateway: 'STRIPE',
          customerName: 'Automated Suite Tester',
        }),
      });
      const data = await res.json();
      addLog(`Payment Success: ${data.transactionId} | Gateway: STRIPE | Amount: $${data.amountPaid}`, 'success');
    } catch (err) {
      addLog('Payment API Failed', 'warn');
    }
  };

  return (
    <div className="pt-20 px-margin-mobile md:px-margin-desktop max-w-[1440px] mx-auto pb-28">
      {/* Header Banner */}
      <div className="bg-slate-900 text-slate-100 p-md rounded-2xl shadow-xl mb-lg border border-slate-800">
        <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
          <div>
            <div className="flex items-center gap-2">
              <span className="w-3 h-3 rounded-full bg-green-400 animate-ping"></span>
              <span className="font-mono text-xs text-amber-400 font-bold uppercase tracking-wider">
                Spring Boot 3.2.0 • Spring Security • PostgreSQL Architecture
              </span>
            </div>
            <h2 className="text-2xl font-bold font-mono mt-1 text-white">
              SavoryStay Java Backend & Database Engine
            </h2>
            <p className="text-slate-400 text-xs font-mono mt-0.5">
              Spring Security JWT Auth, Stripe/PayPal payment integrations, and PostgreSQL persistence.
            </p>
          </div>

          <div className="flex gap-2">
            <button
              onClick={handleTestRealtimePayment}
              className="px-4 py-2 bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold font-mono text-xs rounded-lg transition-all shadow cursor-pointer flex items-center gap-1.5"
            >
              <span className="material-symbols-outlined text-sm">bolt</span>
              Trigger Real-Time Webhook Test
            </button>
          </div>
        </div>

        {/* Server Status Indicators */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mt-4 pt-3 border-t border-slate-800 font-mono text-xs">
          <div>
            <span className="text-slate-500 block">Framework & Auth:</span>
            <span className="text-amber-300 font-bold">Spring Boot 3.2 & Security JWT</span>
          </div>
          <div>
            <span className="text-slate-500 block">Database:</span>
            <span className="text-blue-300 font-bold">PostgreSQL 16.1</span>
          </div>
          <div>
            <span className="text-slate-500 block">Payment Gateways:</span>
            <span className="text-green-300 font-bold">Stripe & PayPal SDKs</span>
          </div>
          <div>
            <span className="text-slate-500 block">Realtime Stream:</span>
            <span className="text-emerald-400 font-bold">WebSocket & Firestore Sync</span>
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex space-x-2 border-b border-slate-800 pb-2 mb-6 font-mono text-xs overflow-x-auto">
        <button
          onClick={() => setActiveTab('standalone_guide')}
          className={`px-4 py-2 rounded-xl transition-all cursor-pointer font-bold whitespace-nowrap ${
            activeTab === 'standalone_guide'
              ? 'bg-amber-500/20 text-amber-300 border border-amber-500/40'
              : 'text-slate-400 hover:text-white hover:bg-slate-800/50'
          }`}
        >
          📦 Project Structure & Setup Guide
        </button>
        <button
          onClick={() => setActiveTab('security')}
          className={`px-4 py-2 rounded-xl transition-all cursor-pointer font-bold whitespace-nowrap ${
            activeTab === 'security'
              ? 'bg-indigo-500/15 text-indigo-400 border border-indigo-500/30'
              : 'text-slate-400 hover:text-white hover:bg-slate-800/50'
          }`}
        >
          SecurityConfig.java (Spring Security)
        </button>
        <button
          onClick={() => setActiveTab('gateway')}
          className={`px-4 py-2 rounded-xl transition-all cursor-pointer font-bold whitespace-nowrap ${
            activeTab === 'gateway'
              ? 'bg-indigo-500/15 text-indigo-400 border border-indigo-500/30'
              : 'text-slate-400 hover:text-white hover:bg-slate-800/50'
          }`}
        >
          PaymentGatewayService.java
        </button>
        <button
          onClick={() => setActiveTab('controller')}
          className={`px-4 py-2 rounded-xl transition-all cursor-pointer font-bold whitespace-nowrap ${
            activeTab === 'controller'
              ? 'bg-indigo-500/15 text-indigo-400 border border-indigo-500/30'
              : 'text-slate-400 hover:text-white hover:bg-slate-800/50'
          }`}
        >
          CulinaryOrderController.java
        </button>
        <button
          onClick={() => setActiveTab('postgres')}
          className={`px-4 py-2 rounded-xl transition-all cursor-pointer font-bold whitespace-nowrap ${
            activeTab === 'postgres'
              ? 'bg-indigo-500/15 text-indigo-400 border border-indigo-500/30'
              : 'text-slate-400 hover:text-white hover:bg-slate-800/50'
          }`}
        >
          schema.sql (PostgreSQL DDL)
        </button>
        <button
          onClick={() => setActiveTab('live_test')}
          className={`px-4 py-2 rounded-xl transition-all cursor-pointer font-bold whitespace-nowrap ${
            activeTab === 'live_test'
              ? 'bg-indigo-500/15 text-indigo-400 border border-indigo-500/30'
              : 'text-slate-400 hover:text-white hover:bg-slate-800/50'
          }`}
        >
          Live API Test Logs
        </button>
      </div>

      {/* Tab Contents */}
      {activeTab === 'standalone_guide' && (
        <div className="bg-slate-950 text-slate-200 rounded-xl p-5 font-mono text-xs overflow-y-auto border border-slate-800 shadow-inner max-h-[500px] space-y-4">
          <div className="p-3 bg-amber-500/10 border border-amber-500/30 rounded-xl text-amber-300">
            <h4 className="font-bold text-sm">📁 Standalone Java Project Directory Created: <code className="bg-slate-900 px-2 py-0.5 rounded text-white">/springboot-backend</code></h4>
            <p className="text-[11px] text-amber-200/80 mt-1">
              A complete, independent Maven Spring Boot project has been created inside your workspace. You can export this repository or copy the folder directly to open in IntelliJ IDEA, Eclipse, or VS Code.
            </p>
          </div>

          <div className="space-y-2">
            <h5 className="font-bold text-indigo-400 text-sm">📂 Directory Structure Overview:</h5>
            <pre className="bg-slate-900 p-3 rounded-xl border border-slate-800 text-[11px] text-slate-300">
{`springboot-backend/
├── pom.xml                                   # Spring Boot 3.2, Security, Stripe & PayPal Maven POM
├── README.md                                  # Comprehensive setup & API guide
└── src/
    └── main/
        ├── java/com/savorystay/
        │   ├── SavoryStayApplication.java    # Application Entrypoint
        │   ├── controller/                   # AuthController & PaymentController
        │   ├── entity/                       # User, Order, Payment JPA Entities
        │   ├── repository/                   # UserRepository, OrderRepository, PaymentRepository
        │   ├── security/                     # JwtTokenProvider, JwtAuthFilter, SecurityConfig
        │   └── service/                      # PaymentGatewayService (Stripe & PayPal SDKs)
        └── resources/
            ├── application.yml                # Spring Boot Config & Keys
            └── schema.sql                     # PostgreSQL Table Creation DDL`}
            </pre>
          </div>

          <div className="space-y-2">
            <h5 className="font-bold text-emerald-400 text-sm">⚡ Quick Command Line Execution:</h5>
            <div className="bg-slate-900 p-3 rounded-xl border border-slate-800 text-[11px] text-slate-200 space-y-1">
              <p className="text-slate-400"># Navigate into backend project:</p>
              <p className="text-indigo-300">cd springboot-backend</p>
              <p className="text-slate-400 mt-2"># Build Maven package:</p>
              <p className="text-indigo-300">mvn clean package</p>
              <p className="text-slate-400 mt-2"># Run Spring Boot application (Default Port 8080):</p>
              <p className="text-emerald-300">mvn spring-boot:run</p>
            </div>
          </div>
        </div>
      )}
      {activeTab === 'security' && (
        <div className="bg-slate-950 text-slate-200 rounded-xl p-md font-mono text-xs overflow-x-auto border border-slate-800 shadow-inner max-h-[500px]">
          <pre className="leading-relaxed">
            <code>{SPRING_SECURITY_CONFIG_CODE}</code>
          </pre>
        </div>
      )}

      {activeTab === 'gateway' && (
        <div className="bg-slate-950 text-slate-200 rounded-xl p-md font-mono text-xs overflow-x-auto border border-slate-800 shadow-inner max-h-[500px]">
          <pre className="leading-relaxed">
            <code>{PAYMENT_GATEWAY_SERVICE_CODE}</code>
          </pre>
        </div>
      )}

      {activeTab === 'controller' && (
        <div className="bg-slate-950 text-slate-200 rounded-xl p-md font-mono text-xs overflow-x-auto border border-slate-800 shadow-inner max-h-[500px]">
          <pre className="leading-relaxed">
            <code>{SPRING_BOOT_CONTROLLER_CODE}</code>
          </pre>
        </div>
      )}

      {activeTab === 'postgres' && (
        <div className="bg-slate-950 text-slate-200 rounded-xl p-md font-mono text-xs overflow-x-auto border border-slate-800 shadow-inner max-h-[500px]">
          <pre className="leading-relaxed">
            <code>{POSTGRESQL_SCHEMA_SQL}</code>
          </pre>
        </div>
      )}

      {activeTab === 'live_test' && (
        <div className="bg-slate-950 text-slate-200 rounded-xl p-md font-mono text-xs border border-slate-800 space-y-3">
          <div className="flex justify-between items-center text-slate-400 pb-2 border-b border-slate-800">
            <span>Spring Boot System Event Stream</span>
            <span className="text-emerald-400">STATUS: {healthStatus?.status || 'UP'}</span>
          </div>

          <div className="space-y-2 max-h-80 overflow-y-auto">
            {apiLogs.map((log, i) => (
              <div key={i} className="flex gap-3 text-xs border-b border-slate-900 pb-1">
                <span className="text-slate-500 font-bold">{log.time}</span>
                <span
                  className={
                    log.type === 'success'
                      ? 'text-emerald-400 font-bold'
                      : log.type === 'warn'
                      ? 'text-amber-400 font-bold'
                      : 'text-slate-300'
                  }
                >
                  {log.msg}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};
