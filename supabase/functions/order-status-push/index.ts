// Supabase Edge Function: order-status-push
// Trigger via Supabase Database Webhook on `public.order_status` (INSERT / UPDATE).
// Required Supabase Secrets:
//   - SUPABASE_URL
//   - SUPABASE_SERVICE_ROLE_KEY
//   - VAPID_PUBLIC_KEY
//   - VAPID_PRIVATE_KEY
//   - VAPID_SUBJECT (e.g. mailto:alejandromoises89@gmail.com)

import webpush from "npm:web-push@3.6.7";

const STATUS_MESSAGES: Record<string, { title: string; body: string }> = {
  PAYMENT_CONFIRMED: {
    title: "✅ ¡Pago confirmado!",
    body: "Verificamos tu transferencia. Tu pedido pasó a cocina y WhatsApp ya está habilitado."
  },
  PREPARING: {
    title: "🔥 ¡Tu pizza está en el horno!",
    body: "Masa madre artesanal horneándose al momento."
  },
  READY_FOR_PICKUP: {
    title: "📦 ¡Listo para retirar!",
    body: "Tu pedido está empaquetado y te espera en el local."
  },
  OUT_FOR_DELIVERY: {
    title: "🛵 ¡Tu pizza va en camino!",
    body: "Nuestro repartidor salió hacia tu dirección en Ciudad del Este."
  },
  COMPLETED: {
    title: "🍕 ¡Pedido entregado!",
    body: "¡Gracias por elegir Pizza Club!"
  },
  CANCELLED: {
    title: "✕ Pedido cancelado",
    body: "Tu preorden fue cancelada. Escribinos por WhatsApp si necesitás ayuda."
  }
};

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "Method not allowed" }), { status: 405 });
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL") || "";
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
  const vapidPublic = Deno.env.get("VAPID_PUBLIC_KEY") || "";
  const vapidPrivate = Deno.env.get("VAPID_PRIVATE_KEY") || "";
  const vapidSubject = Deno.env.get("VAPID_SUBJECT") || "mailto:alejandromoises89@gmail.com";

  if (!supabaseUrl || !serviceKey || !vapidPublic || !vapidPrivate) {
    return new Response(
      JSON.stringify({ error: "Missing SUPABASE or VAPID environment secrets" }),
      { status: 500, headers: { "Content-Type": "application/json" } }
    );
  }

  webpush.setVapidDetails(vapidSubject, vapidPublic, vapidPrivate);

  const event = await req.json();
  const record = event?.record;
  const oldRecord = event?.old_record || {};
  if (!record?.id || !record?.status || record.status === oldRecord.status) {
    return new Response(JSON.stringify({ skipped: true }), {
      headers: { "Content-Type": "application/json" }
    });
  }

  const subsRes = await fetch(
    `${supabaseUrl}/rest/v1/push_subscriptions?order_ids=cs.${encodeURIComponent(JSON.stringify([record.id]))}&select=id,endpoint,subscription`,
    {
      headers: {
        apikey: serviceKey,
        Authorization: `Bearer ${serviceKey}`
      }
    }
  );
  const subscriptions = await subsRes.json();
  if (!Array.isArray(subscriptions) || subscriptions.length === 0) {
    return new Response(JSON.stringify({ sent: 0 }), {
      headers: { "Content-Type": "application/json" }
    });
  }

  const copy = STATUS_MESSAGES[record.status] || {
    title: "🍕 Actualización de tu pedido",
    body: `Nuevo estado: ${record.status}`
  };
  const payload = JSON.stringify({
    orderId: record.id,
    orderNumber: record.order_number,
    status: record.status,
    previousStatus: oldRecord.status || null,
    whatsappUnlocked: Boolean(record.whatsapp_unlocked),
    title: `${copy.title} (${record.order_number})`,
    body: copy.body
  });

  let sent = 0;
  for (const row of subscriptions) {
    try {
      await webpush.sendNotification(row.subscription, payload);
      sent += 1;
    } catch (err: unknown) {
      const statusCode = (err as { statusCode?: number })?.statusCode;
      if (statusCode === 404 || statusCode === 410) {
        await fetch(`${supabaseUrl}/rest/v1/push_subscriptions?id=eq.${encodeURIComponent(row.id)}`, {
          method: "DELETE",
          headers: {
            apikey: serviceKey,
            Authorization: `Bearer ${serviceKey}`
          }
        });
      }
    }
  }

  return new Response(JSON.stringify({ sent }), {
    headers: { "Content-Type": "application/json" }
  });
});
